/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdklib.internal.repository.packages;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.archives.Archive;
import com.android.sdklib.internal.repository.archives.Archive.Arch;
import com.android.sdklib.internal.repository.archives.Archive.Os;
import com.android.sdklib.internal.repository.sources.SdkSource;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.util.GrabProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.IProcessOutput;
import com.android.sdklib.util.GrabProcessOutput.Wait;

import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a tool XML node in an SDK repository.
 */
public class ToolPackage extends FullRevisionPackage implements IMinPlatformToolsDependency {

    /** The value returned by {@link ToolPackage#installId()}. */
    public static final String INSTALL_ID = "tools";                             //$NON-NLS-1$
    /** The value returned by {@link ToolPackage#installId()}. */
    private static final String INSTALL_ID_PREVIEW = "tools-preview";            //$NON-NLS-1$

    /**
     * The minimal revision of the platform-tools package required by this package
     * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
     */
    private final FullRevision mMinPlatformToolsRevision;

    /**
     * Creates a new tool package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    public ToolPackage(SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mMinPlatformToolsRevision = PackageParserUtils.parseFullRevisionElement(
                PackageParserUtils.findChildElement(packageNode,
                                                    SdkRepoConstants.NODE_MIN_PLATFORM_TOOLS_REV));

        if (mMinPlatformToolsRevision.equals(MIN_PLATFORM_TOOLS_REV_INVALID)) {
            // This revision number is mandatory starting with sdk-repository-3.xsd
            // and did not exist before. Complain if the URI has level >= 3.

            boolean needRevision = false;

            Pattern nsPattern = Pattern.compile(SdkRepoConstants.NS_PATTERN);
            Matcher m = nsPattern.matcher(nsUri);
            if (m.matches()) {
                String version = m.group(1);
                try {
                    needRevision = Integer.parseInt(version) >= 3;
                } catch (NumberFormatException e) {
                    // ignore. needRevision defaults to false
                }
            }

            if (needRevision) {
                throw new IllegalArgumentException(
                        String.format("Missing %1$s element in %2$s package",
                                SdkRepoConstants.NODE_MIN_PLATFORM_TOOLS_REV,
                                SdkRepoConstants.NODE_PLATFORM_TOOL));
            }
        }
    }

    /**
     * Manually create a new package with one archive and the given attributes or properties.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public static Package create(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {
        return new ToolPackage(source, props, revision, license, description,
                descUrl, archiveOs, archiveArch, archiveOsPath);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected ToolPackage(
                SdkSource source,
                Properties props,
                int revision,
                String license,
                String description,
                String descUrl,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOs,
                archiveArch,
                archiveOsPath);

        String revStr = getProperty(props, PkgProps.MIN_PLATFORM_TOOLS_REV, null);

        FullRevision rev = MIN_PLATFORM_TOOLS_REV_INVALID;
        if (revStr != null) {
            try {
                rev = FullRevision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {}
        }

        mMinPlatformToolsRevision = rev;
    }

    /**
    * The minimal revision of the tools package required by this package if > 0,
    * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
    * <p/>
    * This attribute is mandatory and should not be normally missing.
     */
    @Override
    public FullRevision getMinPlatformToolsRevision() {
        return mMinPlatformToolsRevision;
    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For tools, we use "tools" or "tools-preview" since this package is unique.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String installId() {
        if (getRevision().isPreview()) {
            return INSTALL_ID_PREVIEW;
        } else {
            return INSTALL_ID;
        }
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String getListDescription() {
        return String.format("Android SDK Tools%1$s",
                isObsolete() ? " (Obsolete)" : "");
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        return String.format("Android SDK Tools, revision %1$s%2$s",
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");
    }

    /** Returns a long description for an {@link IDescription}. */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.length() == 0) {
            s = getShortDescription();
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$s%2$s",
                    getRevision().toShortString(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A "tool" package should always be located in SDK/tools.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
        return new File(osSdkRoot, SdkConstants.FD_TOOLS);
    }

    /**
     * Check whether 2 tool packages are the same <em>and</em> have the
     * same preview bit.
     */
    @Override
    public boolean sameItemAs(Package pkg) {
        // Only one tool package so any tool package is the same item
        return sameItemAs(pkg, false /*ignorePreviews*/);
    }

    @Override
    public boolean sameItemAs(Package pkg, boolean ignorePreviews) {
        // only one tool package so any tool package is the same item.
        if (pkg instanceof ToolPackage) {
            if (ignorePreviews) {
                return true;
            } else {
                // however previews can only match previews by default, unless we ignore that check.
                return ((ToolPackage) pkg).getRevision().isPreview() ==
                    getRevision().isPreview();
            }
        }
        return false;
    }

    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);

        if (!getMinPlatformToolsRevision().equals(MIN_PLATFORM_TOOLS_REV_INVALID)) {
            props.setProperty(PkgProps.MIN_PLATFORM_TOOLS_REV,
                    getMinPlatformToolsRevision().toShortString());
        }
    }

    /**
     * The tool package executes tools/lib/post_tools_install[.bat|.sh]
     * {@inheritDoc}
     */
    @Override
    public void postInstallHook(Archive archive, final ITaskMonitor monitor, File installFolder) {
        super.postInstallHook(archive, monitor, installFolder);

        if (installFolder == null) {
            return;
        }

        File libDir = new File(installFolder, SdkConstants.FD_LIB);
        if (!libDir.isDirectory()) {
            return;
        }

        String scriptName = "post_tools_install";   //$NON-NLS-1$
        String shell = "";                          //$NON-NLS-1$
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            shell = "cmd.exe /c ";                  //$NON-NLS-1$
            scriptName += ".bat";                   //$NON-NLS-1$
        } else {
            scriptName += ".sh";                    //$NON-NLS-1$
        }

        File scriptFile = new File(libDir, scriptName);
        if (!scriptFile.isFile()) {
            return;
        }

        int status = -1;

        try {
            Process proc = Runtime.getRuntime().exec(
                    shell + scriptName, // command
                    null,       // environment
                    libDir);    // working dir

            final String tag = scriptName;
            status = GrabProcessOutput.grabProcessOutput(
                    proc,
                    Wait.WAIT_FOR_PROCESS,
                    new IProcessOutput() {
                        @Override
                        public void out(@Nullable String line) {
                            if (line != null) {
                                monitor.log("[%1$s] %2$s", tag, line);
                            }
                        }

                        @Override
                        public void err(@Nullable String line) {
                            if (line != null) {
                                monitor.logError("[%1$s] Error: %2$s", tag, line);
                            }
                        }
                    });

        } catch (Exception e) {
            monitor.logError("Exception: %s", e.toString());
        }

        if (status != 0) {
            monitor.logError("Failed to execute %s", scriptName);
            return;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                 + ((mMinPlatformToolsRevision == null) ? 0 : mMinPlatformToolsRevision.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof ToolPackage)) {
            return false;
        }
        ToolPackage other = (ToolPackage) obj;
        if (mMinPlatformToolsRevision == null) {
            if (other.mMinPlatformToolsRevision != null) {
                return false;
            }
        } else if (!mMinPlatformToolsRevision.equals(other.mMinPlatformToolsRevision)) {
            return false;
        }
        return true;
    }
}
