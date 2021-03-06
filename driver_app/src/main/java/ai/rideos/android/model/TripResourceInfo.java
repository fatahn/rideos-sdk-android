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
package ai.rideos.android.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

public class TripResourceInfo implements Serializable {
    private final int numPassengers;
    private final String nameOfTripRequester;
    @Nullable
    private final String phoneNumber;

    public TripResourceInfo(final int numPassengers, final String nameOfTripRequester) {
        this(numPassengers, nameOfTripRequester, null);
    }

    public TripResourceInfo(final int numPassengers,
                            final String nameOfTripRequester,
                            @Nullable final String phoneNumber) {
        this.numPassengers = numPassengers;
        this.nameOfTripRequester = nameOfTripRequester;
        this.phoneNumber = phoneNumber;
    }

    public int getNumPassengers() {
        return numPassengers;
    }

    public String getNameOfTripRequester() {
        return nameOfTripRequester;
    }

    public Optional<String> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof TripResourceInfo)) {
            return false;
        }
        final TripResourceInfo that = (TripResourceInfo) other;
        return numPassengers == that.numPassengers
            && nameOfTripRequester.equals(that.nameOfTripRequester)
            && Objects.equals(phoneNumber, that.phoneNumber);
    }
}
