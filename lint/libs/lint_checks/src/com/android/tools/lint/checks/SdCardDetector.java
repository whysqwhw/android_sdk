/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.io.File;
import java.util.Collections;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Node;
import lombok.ast.StringLiteral;

/**
 * Looks for hardcoded references to /sdcard/.
 */
public class SdCardDetector extends Detector implements Detector.JavaScanner {
    /** Hardcoded /sdcard/ references */
    public static final Issue ISSUE = Issue.create(
            "SdCardPath", //$NON-NLS-1$
            "Looks for hardcoded references to /sdcard",

            "Your code should not reference the /sdcard path directly; instead use " +
            "Environment.getExternalStorageDirectory().getPath()",

            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            SdCardDetector.class,
            Scope.JAVA_FILE_SCOPE).setMoreInfo(
            "http://developer.android.com/guide/topics/data/data-storage.html#filesExternal"); //$NON-NLS-1$

    /** Constructs a new {@link SdCardDetector} check */
    public SdCardDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements JavaScanner ----

    @Override
    public List<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends Node>>singletonList(StringLiteral.class);
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        return new StringChecker(context);
    }

    private static class StringChecker extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public StringChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitStringLiteral(StringLiteral node) {
            String s = node.astValue();
            // Other potential String prefixes to check for:
            //    /mnt/sdcard/
            //    /system/media/sdcard
            //    file://sdcard
            //    file:///sdcard
            if (s.startsWith("/sdcard")) { //$NON-NLS-1$
                String message = "Do not hardcode \"/sdcard/\"; " +
                    "use Environment.getExternalStorageDirectory().getPath() instead";
                Location location = mContext.getLocation(node);
                mContext.report(ISSUE, node, location, message, s);
            }

            return false;
        }
    }
}
