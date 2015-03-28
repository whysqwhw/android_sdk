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

import java.awt.Point;
import java.io.File;

public class Meta {
    File mIconSixtyFour;
    File mIconSixteen;
    File mFrame;
    Point mFrameOffsetLandscape;
    Point mFrameOffsetPortrait;

    public File getIconSixtyFour() {
        return mIconSixtyFour;
    }

    public boolean hasIconSixtyFour() {
        if (mIconSixtyFour != null && mIconSixtyFour.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public File getIconSixteen() {
        return mIconSixteen;
    }

    public boolean hasIconSixteen() {
        if (mIconSixteen != null && mIconSixteen.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public File getFrame() {
        return mFrame;
    }

    public boolean hasFrame() {
        if (mFrame != null && mFrame.isFile()) {
            return true;
        } else {
            return false;
        }
    }

    public Point getFrameOffsetLandscape() {
        return mFrameOffsetLandscape;
    }

    public Point getFrameOffsetPortrait() {
        return mFrameOffsetPortrait;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Meta)) {
            return false;
        }
        Meta m = (Meta) o;

        // Note that any of the fields of either object can be null
        if (mIconSixtyFour != null && !mIconSixtyFour.equals(m.getIconSixtyFour())){
            return false;
        } else if (m.getIconSixtyFour() != null && !m.getIconSixtyFour().equals(mIconSixtyFour)) {
            return false;
        }

        if (mIconSixteen != null && !mIconSixteen.equals(m.getIconSixteen())){
            return false;
        } else if (m.getIconSixteen() != null && !m.getIconSixteen().equals(mIconSixteen)) {
            return false;
        }

        if (mFrame != null && !mFrame.equals(m.getFrame())) {
            return false;
        } else if (m.getFrame() != null && !m.getFrame().equals(mFrame)) {
            return false;
        }

        if (mFrameOffsetLandscape != null
                && !mFrameOffsetLandscape.equals(m.getFrameOffsetLandscape())){
            return false;
        } else if (m.getFrameOffsetLandscape() != null
                && !m.getFrameOffsetLandscape().equals(mFrameOffsetLandscape)){
            return false;
        }


        if (mFrameOffsetPortrait != null
                && !mFrameOffsetPortrait.equals(m.getFrameOffsetPortrait())){
            return false;
        } else if (m.getFrameOffsetPortrait() != null
                && !m.getFrameOffsetPortrait().equals(mFrameOffsetPortrait)){
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        if(mIconSixteen != null){
            hash = 31 * hash + mIconSixteen.hashCode();
        }
        if(mIconSixtyFour != null){
            hash = 31 * hash + mIconSixtyFour.hashCode();
        }
        if(mFrame != null){
            hash = 31 * hash + mFrame.hashCode();
        }
        if(mFrameOffsetLandscape != null){
            hash = 31 * hash + mFrameOffsetLandscape.hashCode();
        }
        if(mFrameOffsetPortrait != null){
            hash = 31 * hash + mFrameOffsetPortrait.hashCode();
        }
        return hash;
    }
}
