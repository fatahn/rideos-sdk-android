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
package ai.rideos.android.driver_app.online.driving.drive_pending;

import ai.rideos.android.common.device.DeviceLocator;
import ai.rideos.android.common.interactors.RouteInteractor;
import ai.rideos.android.common.model.LatLng;
import ai.rideos.android.common.model.LocationAndHeading;
import ai.rideos.android.common.model.RouteInfoModel;
import ai.rideos.android.common.model.map.CameraUpdate;
import ai.rideos.android.common.model.map.LatLngBounds;
import ai.rideos.android.common.reactive.SchedulerProviders.TrampolineSchedulerProvider;
import ai.rideos.android.common.reactive.TestUtils;
import ai.rideos.android.common.utils.Markers;
import ai.rideos.android.common.view.resources.ResourceProvider;
import ai.rideos.android.common.view.strings.RouteFormatter;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class DefaultDrivePendingViewModelTest {
    private static final LatLng ORIGIN = new LatLng(0, 0);
    private static final LatLng DESTINATION = new LatLng(1, 1);
    private static final RouteInfoModel ROUTE = new RouteInfoModel(
        Arrays.asList(ORIGIN, DESTINATION),
        10,
        1000
    );
    private static final String SUCCESS_ROUTE_DISPLAY = "success";

    private DefaultDrivePendingViewModel viewModelUnderTest;
    private RouteInteractor routeInteractor;

    @Before
    public void setUp() {
        final DeviceLocator deviceLocator = Mockito.mock(DeviceLocator.class);
        Mockito.when(deviceLocator.getLastKnownLocation()).thenReturn(
            Single.just(new LocationAndHeading(ORIGIN, 0f))
        );

        routeInteractor = Mockito.mock(RouteInteractor.class);
        Mockito.when(routeInteractor.getRoute(ORIGIN, DESTINATION))
            .thenReturn(Observable.just(ROUTE));

        final RouteFormatter routeFormatter = Mockito.mock(RouteFormatter.class);
        Mockito.when(routeFormatter.getDisplayStringForRouteResult(
            Matchers.argThat(TestUtils.matchResultWithOutcome(true)))
        )
            .thenReturn(SUCCESS_ROUTE_DISPLAY);

        viewModelUnderTest = new DefaultDrivePendingViewModel(
            deviceLocator,
            routeInteractor,
            Mockito.mock(ResourceProvider.class),
            DESTINATION,
            new TrampolineSchedulerProvider(),
            routeFormatter
        );
    }

    @Test
    public void testGetRouteDetailFromRouteInteractor() {
        viewModelUnderTest.getRouteDetailText().test()
            .assertValueCount(1)
            .assertValueAt(0, SUCCESS_ROUTE_DISPLAY);
        Mockito.verify(routeInteractor).getRoute(ORIGIN, DESTINATION);
    }

    @Test
    public void testGetCameraUpdateZoomsOnRoute() {
        final LatLngBounds expectedBounds = new LatLngBounds(ORIGIN, DESTINATION);
        final CameraUpdate expectedUpdate = CameraUpdate.fitToBounds(expectedBounds);

        viewModelUnderTest.getCameraUpdates().test()
            .assertValueCount(1)
            .assertValueAt(0, expectedUpdate);
    }

    @Test
    public void testGetMarkersReturnsOriginAndDestination() {
        viewModelUnderTest.getMarkers().test()
            .assertValueCount(1)
            .assertValueAt(0, map ->
                map.get(Markers.PICKUP_MARKER_KEY).getPosition().equals(ORIGIN)
                    && map.get(Markers.DROP_OFF_MARKER_KEY).getPosition().equals(DESTINATION)
            );
    }

    @Test
    public void testGetPathsReturnsRoute() {
        viewModelUnderTest.getPaths().test()
            .assertValueCount(1)
            .assertValueAt(0, paths ->
                paths.size() == 1
                    && paths.get(0).getCoordinates().equals(ROUTE.getRoute())
            );
    }
}
