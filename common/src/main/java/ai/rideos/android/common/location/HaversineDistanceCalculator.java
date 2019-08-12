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
package ai.rideos.android.common.location;

import ai.rideos.android.common.model.LatLng;
import android.location.Location;

public class HaversineDistanceCalculator implements DistanceCalculator {

    @Override
    public double getDistanceInMeters(final LatLng origin, final LatLng destination) {
        Location originLocation = new Location("");
        Location destinationLocation = new Location("");

        originLocation.setLatitude(origin.getLatitude());
        originLocation.setLongitude(origin.getLongitude());

        destinationLocation.setLatitude(destination.getLatitude());
        destinationLocation.setLongitude(destination.getLongitude());

        return originLocation.distanceTo(destinationLocation);
    }
}
