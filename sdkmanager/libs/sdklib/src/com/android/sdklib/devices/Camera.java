/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.devices;

public class Camera {
    CameraLocation mLocation;
    boolean mAutofocus;
    boolean mFlash;

    public CameraLocation getLocation() {
        return mLocation;
    }

    public boolean hasAutofocus() {
        return mAutofocus;
    }

    public boolean hasFlash() {
        return mFlash;
    }

    /**
     * Returns a copy of the object that shares no state with it,
     * but is initialized to equivalent values.
     *
     * @return A copy of the object.
     */
    public Camera deepCopy() {
        Camera c = new Camera();
        c.mLocation = mLocation;
        c.mAutofocus = mAutofocus;
        c.mFlash = mFlash;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Camera)) {
            return false;
        }
        Camera c = (Camera) o;
        return mLocation == c.mLocation
                && mAutofocus == c.hasAutofocus()
                && mFlash == c.hasFlash();
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mLocation.hashCode();
        hash = 31 * hash + (mAutofocus ? 1 : 0);
        hash = 31 * hash + (mFlash ? 1 : 0);
        return hash;
    }
}
