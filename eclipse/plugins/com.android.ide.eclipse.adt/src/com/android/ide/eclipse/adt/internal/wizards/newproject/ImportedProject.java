/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.wizards.newproject;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.io.FolderWrapper;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.xml.AndroidManifestParser;
import com.android.sdklib.xml.ManifestData;
import com.android.sdklib.xml.ManifestData.Activity;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.xml.sax.SAXException;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An Android project to be imported */
class ImportedProject {
    private final File mLocation;
    private String mActivityName;
    private ManifestData mManifest;
    private String mProjectName;

    ImportedProject(File location) {
        super();
        mLocation = location;
    }

    File getLocation() {
        return mLocation;
    }

    @Nullable
    ManifestData getManifest() {
        if (mManifest == null) {
            try {
                mManifest = AndroidManifestParser.parse(new FolderWrapper(mLocation));
            } catch (SAXException e) {
                // Some sort of error in the manifest file: report to the user in a better way?
                AdtPlugin.log(e, null);
                return null;
            } catch (Exception e) {
                AdtPlugin.log(e, null);
                return null;
            }
        }

        return mManifest;
    }

    @Nullable
    public String getActivityName() {
        if (mActivityName == null) {
            // Compute the project name and the package name from the manifest
            ManifestData manifest = getManifest();
            if (manifest != null) {
                if (manifest.getLauncherActivity() != null) {
                    mActivityName = manifest.getLauncherActivity().getName();
                }
                if (mActivityName == null || mActivityName.isEmpty()) {
                    Activity[] activities = manifest.getActivities();
                    for (Activity activity : activities) {
                        mActivityName = activity.getName();
                        if (mActivityName != null && !mActivityName.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }

        return mActivityName;
    }

    @NonNull
    public String getProjectName() {
        if (mProjectName == null) {
            String activityName = getActivityName();
            if (activityName == null || activityName.isEmpty()) {
                // I could also look at the build files, say build.xml from ant, and
                // try to glean the project name from there
                mProjectName = mLocation.getName();
            } else {
                // Try to derive it from the activity name:
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                IStatus nameStatus = workspace.validateName(activityName, IResource.PROJECT);
                if (nameStatus.isOK()) {
                    mProjectName = activityName;
                } else {
                    // Try to derive it by escaping characters
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0, n = activityName.length(); i < n; i++) {
                        char c = activityName.charAt(i);
                        if (c != IPath.DEVICE_SEPARATOR && c != IPath.SEPARATOR && c != '\\') {
                            sb.append(c);
                        }
                    }
                    if (sb.length() == 0) {
                        mProjectName = mLocation.getName();
                    } else {
                        mProjectName = sb.toString();
                    }
                }
            }
        }

        return mProjectName;
    }

    public IAndroidTarget getTarget() {
        // Pick a target:
        // First try to find the one requested by project.properties
        IAndroidTarget[] targets = Sdk.getCurrent().getTargets();
        ProjectProperties properties = ProjectProperties.load(mLocation.getPath(),
                PropertyType.PROJECT);
        if (properties != null) {
            String targetProperty = properties.getProperty(ProjectProperties.PROPERTY_TARGET);
            if (targetProperty != null) {
                Matcher m = Pattern.compile("android-(.+)").matcher( //$NON-NLS-1$
                        targetProperty.trim());
                if (m.matches()) {
                    String targetName = m.group(1);
                    int targetLevel;
                    try {
                        targetLevel = Integer.parseInt(targetName);
                    } catch (NumberFormatException nufe) {
                        // pass
                        targetLevel = -1;
                    }
                    for (IAndroidTarget t : targets) {
                        AndroidVersion version = t.getVersion();
                        if (version.isPreview() && targetName.equals(version.getCodename())) {
                            return t;
                        } else if (targetLevel == version.getApiLevel()) {
                            return t;
                        }
                    }
                    if (targetLevel > 0) {
                        // If not found, pick the closest one that is higher than the
                        // api level
                        IAndroidTarget target = targets[targets.length - 1];
                        int targetDelta = target.getVersion().getApiLevel() - targetLevel;
                        for (IAndroidTarget t : targets) {
                            int newDelta = t.getVersion().getApiLevel() - targetLevel;
                            if (newDelta >= 0 && newDelta < targetDelta) {
                                targetDelta = newDelta;
                                target = t;
                            }
                        }

                        return target;
                    }
                }
            }
        }

        // If not found, pick the closest one to the one requested by the
        // project (in project.properties) that is still >= the minSdk version
        IAndroidTarget target = targets[targets.length - 1];
        ManifestData manifest = getManifest();
        if (manifest != null) {
            int minSdkLevel = manifest.getMinSdkVersion();
            int targetDelta = target.getVersion().getApiLevel() - minSdkLevel;
            for (IAndroidTarget t : targets) {
                int newDelta = t.getVersion().getApiLevel() - minSdkLevel;
                if (newDelta >= 0 && newDelta < targetDelta) {
                    targetDelta = newDelta;
                    target = t;
                }
            }
        }

        return target;
    }
}