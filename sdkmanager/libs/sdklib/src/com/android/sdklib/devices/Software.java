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

import java.util.Set;

public class Software {
    int mMinSdkLevel = 0;
    int mMaxSdkLevel = Integer.MAX_VALUE;
    boolean mLiveWallpaperSupport;
    Set<BluetoothProfile> mBluetoothProfiles;
    String mGlVersion;
    Set<String> mGlExtensions;

    public int getMinSdkLevel() {
        return mMinSdkLevel;
    }

    public int getMaxSdkLevel() {
        return mMaxSdkLevel;
    }

    public boolean hasLiveWallpaperSupport() {
        return mLiveWallpaperSupport;
    }

    public Set<BluetoothProfile> getBluetoothProfiles() {
        return mBluetoothProfiles;
    }

    public String getGlVersion() {
        return mGlVersion;
    }

    public Set<String> getGlExtensions() {
        return mGlExtensions;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Software)) {
            return false;
        }

        Software sw = (Software) o;
        return mMinSdkLevel == sw.getMinSdkLevel()
                && mMaxSdkLevel == sw.getMaxSdkLevel()
                && mLiveWallpaperSupport == sw.hasLiveWallpaperSupport()
                && mBluetoothProfiles.equals(sw.getBluetoothProfiles())
                && mGlVersion.equals(sw.getGlVersion())
                && mGlExtensions.equals(sw.getGlExtensions());
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mMinSdkLevel;
        hash = 31 * hash + mMaxSdkLevel;
        hash = 31 * hash + (mLiveWallpaperSupport ? 1 : 0);
        hash = 31 * hash + mBluetoothProfiles.hashCode();
        hash = 31 * hash + mGlVersion.hashCode();
        hash = 31 * hash + mGlExtensions.hashCode();
        return hash;
    }
}
