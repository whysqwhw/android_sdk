/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.welcome;

import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtPlugin.CheckSdkErrorHandler;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutWindowCoordinator;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.util.GrabProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.IProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.Wait;
import com.android.sdkstats.DdmsPreferenceStore;
import com.android.sdkstats.SdkStatsService;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADT startup tasks (other than those performed in {@link AdtPlugin#start(org.osgi.framework.BundleContext)}
 * when the plugin is initializing.
 * <p>
 * The main tasks currently performed are:
 * <ul>
 *   <li> See if the user has ever run the welcome wizard, and if not, run it
 *   <li> Ping the usage statistics server, if enabled by the user. This is done here
 *       rather than during the plugin start since this task is run later (when the workspace
 *       is fully initialized) and we want to ask the user for permission for usage
 *       tracking before running it (and if we don't, then the usage tracking permissions
 *       dialog will run instead.)
 * </ul>
 */
public class AdtStartup implements IStartup {

    private DdmsPreferenceStore mStore = new DdmsPreferenceStore();

    @Override
    public void earlyStartup() {
        if (isFirstTime()) {
            showWelcomeWizard();
            // Usage statistics are sent after the wizard has run asynchronously (provided the
            // user opted in)
        } else if (mStore.isPingOptIn()) {
            sendUsageStats();
        }

        initializeWindowCoordinator();

        AdtPlugin.getDefault().workbenchStarted();
    }

    private boolean isFirstTime() {
        // If we already have a known SDK location in our workspace then we know this
        // is not the first time this user is running ADT.
        String osSdkFolder = AdtPrefs.getPrefs().getOsSdkFolder();
        if (osSdkFolder != null && osSdkFolder.length() > 0) {
            return false;
        }

        for (int i = 0; i < 2; i++) {
            String osSdkPath = null;

            if (i == 0) {
                // If we've recorded an SDK location in the .android settings, then the user
                // has run ADT before but possibly in a different workspace. We don't want to pop up
                // the welcome wizard each time if we can simply use the existing SDK install.
                osSdkPath = mStore.getLastSdkPath();
            } else if (i == 1) {
                osSdkPath = getSdkPathFromWindowsRegistry();
            }

            if (osSdkPath != null && osSdkPath.length() > 0) {
                boolean ok = new File(osSdkPath).isDirectory();

                if (!ok) {
                    osSdkPath = osSdkPath.trim();
                    ok = new File(osSdkPath).isDirectory();
                }

                if (ok) {
                    // Verify that the SDK is valid
                    ok = AdtPlugin.getDefault().checkSdkLocationAndId(osSdkPath,
                            new AdtPlugin.CheckSdkErrorHandler() {
                        @Override
                        public boolean handleError(
                                CheckSdkErrorHandler.Solution solution,
                                String message) {
                            return false;
                        }

                        @Override
                        public boolean handleWarning(
                                CheckSdkErrorHandler.Solution  solution,
                                String message) {
                            return true;
                        }
                    });
                    if (ok) {
                        // Yes, we've seen an SDK location before and we can use it again,
                        // no need to pester the user with the welcome wizard.
                        // This also implies that the user has responded to the usage statistics
                        // question.
                        AdtPrefs.getPrefs().setSdkLocation(new File(osSdkPath));
                        return false;
                    }
                }
            }
        }

        // Check whether we've run this wizard before.
        return !mStore.isAdtUsed();
    }

    private String getSdkPathFromWindowsRegistry() {
        if (SdkConstants.CURRENT_PLATFORM != SdkConstants.PLATFORM_WINDOWS) {
            return null;
        }

        final String valueName = "Path";                                               //$NON-NLS-1$
        final AtomicReference<String> result = new AtomicReference<String>();
        final Pattern regexp =
            Pattern.compile("^\\s+" + valueName + "\\s+REG_SZ\\s+(.*)$");//$NON-NLS-1$ //$NON-NLS-2$

        for (String key : new String[] {
                "HKLM\\Software\\Android SDK Tools",                                   //$NON-NLS-1$
                "HKLM\\Software\\Wow6432Node\\Android SDK Tools" }) {                  //$NON-NLS-1$

            String[] command = new String[] {
                "reg", "query", key, "/v", valueName       //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            };

            Process process;
            try {
                process = Runtime.getRuntime().exec(command);

                GrabProcessOutput.grabProcessOutput(
                        process,
                        Wait.WAIT_FOR_READERS,
                        new IProcessOutput() {
                            @Override
                            public void out(@Nullable String line) {
                                if (line != null) {
                                    Matcher m = regexp.matcher(line);
                                    if (m.matches()) {
                                        result.set(m.group(1));
                                    }
                                }
                            }

                            @Override
                            public void err(@Nullable String line) {
                                // ignore stderr
                            }
                        });
            } catch (IOException ignore) {
            } catch (InterruptedException ignore) {
            }

            String str = result.get();
            if (str != null) {
                if (new File(str).isDirectory()) {
                    return str;
                }
                str = str.trim();
                if (new File(str).isDirectory()) {
                    return str;
                }
            }
        }

        return null;
    }

    private void initializeWindowCoordinator() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if (window != null) {
                    LayoutWindowCoordinator.start(window);
                }
            }
        });
    }

    private void showWelcomeWizard() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if (window != null) {
                    WelcomeWizard wizard = new WelcomeWizard(mStore);
                    WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
                    dialog.open();
                }

                // Record the fact that we've run the wizard so we don't attempt to do it again,
                // even if the user just cancels out of the wizard.
                mStore.setAdtUsed(true);

                if (mStore.isPingOptIn()) {
                    sendUsageStats();
                }
            }
        });
    }

    private void sendUsageStats() {
        // Ping the usage server and parse the SDK content.
        // This is deferred in separate jobs to avoid blocking the bundle start.
        // We also serialize them to avoid too many parallel jobs when Eclipse starts.
        Job pingJob = createPingUsageServerJob();
        // build jobs are run after other interactive jobs
        pingJob.setPriority(Job.BUILD);
        // Wait another 30 seconds before starting the ping job. This gives other
        // startup tasks time to finish since it's not vital to get the usage ping
        // immediately.
        pingJob.schedule(30000 /*milliseconds*/);
    }

    /**
     * Creates a job than can ping the usage server.
     */
    private Job createPingUsageServerJob() {
        // In order to not block the plugin loading, so we spawn another thread.
        Job job = new Job("Android SDK Ping") {  // Job name, visible in progress view
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    pingUsageServer();

                    return Status.OK_STATUS;
                } catch (Throwable t) {
                    AdtPlugin.log(t, "pingUsageServer failed");       //$NON-NLS-1$
                    return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                            "pingUsageServer failed", t);    //$NON-NLS-1$
                }
            }
        };
        return job;
    }

    private static Version getVersion(Plugin plugin) {
        @SuppressWarnings("cast") // Cast required in Eclipse 3.5; prevent auto-removal in 3.7
        String version = (String) plugin.getBundle().getHeaders().get(Constants.BUNDLE_VERSION);
        // Parse the string using the Version class.
        return new Version(version);
    }

    /**
     * Pings the usage start server.
     */
    private void pingUsageServer() {
        // Report the version of the ADT plugin to the stat server
        Version version = getVersion(AdtPlugin.getDefault());
        String adtVersionString = String.format("%1$d.%2$d.%3$d", version.getMajor(), //$NON-NLS-1$
                version.getMinor(), version.getMicro());

        // Report the version of Eclipse to the stat server.
        // Get the version of eclipse by getting the version of one of the runtime plugins.
        Version eclipseVersion = getVersion(ResourcesPlugin.getPlugin());
        String eclipseVersionString = String.format("%1$d.%2$d",  //$NON-NLS-1$
                eclipseVersion.getMajor(), eclipseVersion.getMinor());

        SdkStatsService stats = new SdkStatsService();
        stats.ping("adt", adtVersionString); //$NON-NLS-1$
        stats.ping("eclipse", eclipseVersionString); //$NON-NLS-1$
    }
}
