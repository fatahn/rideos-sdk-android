/**
 * Copyright 2018-2019 rideOS, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.rideos.android.driver_app.online;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.eq;

import ai.rideos.android.common.authentication.User;
import ai.rideos.android.common.device.DeviceLocator;
import ai.rideos.android.common.model.LatLng;
import ai.rideos.android.common.model.LocationAndHeading;
import ai.rideos.android.common.reactive.SchedulerProvider;
import ai.rideos.android.driver_app.online.idle.GoOfflineListener;
import ai.rideos.android.interactors.DriverPlanInteractor;
import ai.rideos.android.model.DriverAlert;
import ai.rideos.android.model.DriverAlert.DriverAlertType;
import ai.rideos.android.model.OnlineViewState;
import ai.rideos.android.model.OnlineViewState.DisplayType;
import ai.rideos.android.model.TripResourceInfo;
import ai.rideos.android.model.VehiclePlan;
import ai.rideos.android.model.VehiclePlan.Action;
import ai.rideos.android.model.VehiclePlan.Action.ActionType;
import ai.rideos.android.model.VehiclePlan.Waypoint;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.internal.schedulers.TrampolineScheduler;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class DefaultOnlineViewModelTest {
    private static final String VEHICLE_ID = "vehicle-1";
    private static final LocationAndHeading LOCATION = new LocationAndHeading(new LatLng(0, 1), 10.0f);
    private static final int POLL_INTERVAL = 100;
    private static final int RETRY_COUNT = 1;
    private static final Waypoint MOCK_WAYPOINT = new Waypoint(
        "task-1",
        Collections.singletonList("step-1"),
        new Action(LOCATION.getLatLng(), ActionType.DRIVE_TO_PICKUP, new TripResourceInfo(1, "Rider"))
    );

    private DefaultOnlineViewModel viewModelUnderTest;
    private DriverPlanInteractor planInteractor;
    private TestScheduler testScheduler;

    @Before
    public void setUp() {
        final PublishSubject<LocationAndHeading> locationSubject = PublishSubject.create();

        final User user = Mockito.mock(User.class);
        Mockito.when(user.getId()).thenReturn(VEHICLE_ID);

        final DeviceLocator deviceLocator = Mockito.mock(DeviceLocator.class);
        Mockito.when(deviceLocator.observeCurrentLocation(anyInt()))
            .thenReturn(locationSubject.observeOn(TrampolineScheduler.instance()));

        planInteractor = Mockito.mock(DriverPlanInteractor.class);

        final GoOfflineListener listener = Mockito.mock(GoOfflineListener.class);

        testScheduler = new TestScheduler();

        viewModelUnderTest = new DefaultOnlineViewModel(
            listener,
            planInteractor,
            ExternalVehicleRouteSynchronizer.NOOP,
            deviceLocator,
            user,
            new CustomTestSchedulerProvider(testScheduler),
            POLL_INTERVAL,
            RETRY_COUNT
        );
    }

    @Test
    public void testFirstStateIsAlwaysIdle() {
        viewModelUnderTest.getOnlineViewState().test()
            .assertValueAt(0, state -> state.getDisplayType() == DisplayType.IDLE);
    }

    @Test
    public void testStateUpdatesToCorrectDisplayWhenWaypointIsReturned() {
        assertDisplayUpdate(ActionType.DRIVE_TO_PICKUP, DisplayType.DRIVING_TO_PICKUP);
        assertDisplayUpdate(ActionType.DRIVE_TO_DROP_OFF, DisplayType.DRIVING_TO_DROP_OFF);
        assertDisplayUpdate(ActionType.LOAD_RESOURCE, DisplayType.WAITING_FOR_PASSENGER);
    }

    private void assertDisplayUpdate(final ActionType stepAction, final DisplayType displayType) {
        final Waypoint currentWaypoint = new Waypoint(
            "task-1",
            Collections.singletonList("step-1"),
            new Action(LOCATION.getLatLng(), stepAction, new TripResourceInfo(1, "Rider"))
        );
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(
                new VehiclePlan(Collections.singletonList(currentWaypoint))
            ));

        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        viewModelUnderTest.getOnlineViewState().test()
            .assertValueAt(0, state -> state.getDisplayType() == displayType
                && state.getCurrentWaypoint().equals(currentWaypoint)
            );
    }

    @Test
    public void testDriverAlertIsEmittedWhenNewTripAddedAndQueueEmpty() {
        final TestObserver<DriverAlert> testObserver = viewModelUnderTest.getDriverAlerts().test();

        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.singletonList(MOCK_WAYPOINT))));
        testScheduler.triggerActions();
        testObserver.assertValueCount(1)
            .assertValueAt(
                0,
                new DriverAlert(DriverAlertType.NEW_REQUEST, MOCK_WAYPOINT.getAction().getTripResourceInfo())
            );

        // Getting the same plan shouldn't emit another alert
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(1);
    }

    @Test
    public void testDriverAlertIsEmittedWhenNewTripAddedAndQueueHasAnotherTrip() {
        final TestObserver<DriverAlert> testObserver = viewModelUnderTest.getDriverAlerts().test();
        final Waypoint secondWaypoint = new Waypoint(
            "task-2",
            Collections.singletonList("step-1"),
            new Action(LOCATION.getLatLng(), ActionType.DRIVE_TO_PICKUP, new TripResourceInfo(2, "Rider2"))
        );

        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.singletonList(MOCK_WAYPOINT))))
            .thenReturn(Observable.just(new VehiclePlan(Arrays.asList(MOCK_WAYPOINT, secondWaypoint))));
        testScheduler.triggerActions();
        testObserver.assertValueCount(1);

        // Getting the same plan shouldn't emit another alert
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2)
            .assertValueAt(
                1,
                new DriverAlert(DriverAlertType.NEW_REQUEST, secondWaypoint.getAction().getTripResourceInfo())
            );
    }

    @Test
    public void testStateIsIdleWhenEmptyPlanIsReturned() {
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.emptyList())));
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        viewModelUnderTest.getOnlineViewState().test()
            .assertValueAt(0, state -> state.getDisplayType() == DisplayType.IDLE);
    }

    @Test
    public void testPlanUpdatesAreIgnoredOnFailure() {
        final TestObserver<OnlineViewState> testObserver = viewModelUnderTest.getOnlineViewState().test();
        testObserver.assertValueCount(1);

        // Return error
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.error(new IOException()));
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(1);

        // Return success
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.singletonList(MOCK_WAYPOINT))));
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2);
    }

    @Test
    public void testDisplayDoesNotUpdateWhenPlanIsTheSame() {
        final TestObserver<OnlineViewState> testObserver = viewModelUnderTest.getOnlineViewState().test();

        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.singletonList(MOCK_WAYPOINT))));
        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2);

        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2);
    }

    @Test
    public void testCompleteWaypointTriggersPlanUpdate() {
        final TestObserver<OnlineViewState> testObserver = viewModelUnderTest.getOnlineViewState().test();

        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Collections.emptyList())))
            .thenReturn(Observable.just(new VehiclePlan(Collections.singletonList(MOCK_WAYPOINT))));
        testScheduler.triggerActions();
        testObserver.assertValueCount(1); // idle plan didn't change

        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        viewModelUnderTest.finishedDriving();
        testObserver.assertValueCount(2);
    }

    @Test
    public void testIfWaypointInTheFutureChangesButTheCurrentIsTheSameDoNotUpdateDisplay() {
        final Waypoint waypoint1 = new Waypoint(
            "task-2",
            Collections.singletonList("step-2"),
            new Action(LOCATION.getLatLng(), ActionType.DRIVE_TO_PICKUP, new TripResourceInfo(1, "Rider"))
        );

        final Waypoint waypoint2 = new Waypoint(
            "task-3",
            Collections.singletonList("step-2"),
            new Action(LOCATION.getLatLng(), ActionType.DRIVE_TO_DROP_OFF, new TripResourceInfo(1, "Rider"))
        );

        // The first time the view model gets the plan, return waypoint1 as the second waypoint. The second time,
        // return waypoint2 as the second waypoint. Since the first waypoint is the same, the display state should
        // not update
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(new VehiclePlan(Arrays.asList(MOCK_WAYPOINT, waypoint1))))
            .thenReturn(Observable.just(new VehiclePlan(Arrays.asList(MOCK_WAYPOINT, waypoint2))));

        final TestObserver<OnlineViewState> testObserver = viewModelUnderTest.getOnlineViewState().test();
        testObserver.assertValueCount(1);

        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2);

        testScheduler.advanceTimeBy(POLL_INTERVAL, TimeUnit.MILLISECONDS);
        testObserver.assertValueCount(2);
    }

    @Test
    public void testOpeningTripDetailsTransitionsToTripDetails() {
        final VehiclePlan vehiclePlan = new VehiclePlan(Collections.emptyList());
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(vehiclePlan));

        viewModelUnderTest.openTripDetails();
        viewModelUnderTest.getOnlineViewState().test()
            .assertValueAt(0, OnlineViewState.tripDetails(vehiclePlan));
    }

    @Test
    public void testClosingTripDetailsTransitionsBackToVehicleState() {
        final VehiclePlan vehiclePlan = new VehiclePlan(Collections.emptyList());
        Mockito.when(planInteractor.getPlanForVehicle(eq(VEHICLE_ID)))
            .thenReturn(Observable.just(vehiclePlan));

        viewModelUnderTest.openTripDetails();
        final TestObserver<OnlineViewState> testObserver = viewModelUnderTest.getOnlineViewState().test();
        testObserver.assertValueAt(0, OnlineViewState.tripDetails(vehiclePlan));

        viewModelUnderTest.closeTripDetails();
        testObserver.assertValueAt(1, OnlineViewState.idle());
    }

    // Only use the test scheduler for advancing the timer on the io thread
    private static class CustomTestSchedulerProvider implements SchedulerProvider {
        private final TestScheduler testScheduler;

        CustomTestSchedulerProvider(final TestScheduler testScheduler) {
            this.testScheduler = testScheduler;
        }

        @Override
        public Scheduler io() {
            return testScheduler;
        }

        @Override
        public Scheduler computation() {
            return Schedulers.trampoline();
        }

        @Override
        public Scheduler mainThread() {
            return Schedulers.trampoline();
        }

        @Override
        public Scheduler single() {
            return Schedulers.trampoline();
        }
    }
}
