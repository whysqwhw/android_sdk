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

import com.android.tools.lint.LintCliXmlParser;
import com.android.tools.lint.LombokParser;
import com.android.tools.lint.Main;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.IDomParser;
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

/** Common utility methods for the various lint check tests */
@SuppressWarnings("javadoc")
public abstract class AbstractCheckTest extends TestCase {
    protected abstract Detector getDetector();

    protected List<Issue> getIssues() {
        List<Issue> issues = new ArrayList<Issue>();
        Class<? extends Detector> detectorClass = getDetector().getClass();
        // Get the list of issues from the registry and filter out others, to make sure
        // issues are properly registered
        List<Issue> candidates = new BuiltinIssueRegistry().getIssues();
        for (Issue issue : candidates) {
            if (issue.getDetectorClass() == detectorClass) {
                issues.add(issue);
            }
        }

        return issues;
    }

    private class CustomIssueRegistry extends IssueRegistry {
        @Override
        public List<Issue> getIssues() {
            return AbstractCheckTest.this.getIssues();
        }
    }

    protected String lintFiles(String... relativePaths) throws Exception {
        List<File> files = new ArrayList<File>();
        for (String relativePath : relativePaths) {
            File file = getTestfile(relativePath);
            assertNotNull(file);
            files.add(file);
        }

        addManifestFile(getTargetDir());

        return checkLint(files);
    }

    protected String checkLint(List<File> files) throws Exception {
        mOutput = new StringBuilder();
        TestLintClient lintClient = new TestLintClient();
        LintDriver driver = new LintDriver(new CustomIssueRegistry(), lintClient);
        driver.analyze(files, null /* scope */);

        List<String> errors = lintClient.getErrors();
        Collections.sort(errors);
        for (String error : errors) {
            if (mOutput.length() > 0) {
                mOutput.append('\n');
            }
            mOutput.append(error);
        }

        if (mOutput.length() == 0) {
            mOutput.append("No warnings.");
        }

        return mOutput.toString();
    }

    /**
     * Run lint on the given files when constructed as a separate project
     * @return The output of the lint check. On Windows, this transforms all directory
     *   separators to the unix-style forward slash.
     */
    protected String lintProject(String... relativePaths) throws Exception {
        assertFalse("getTargetDir must be overridden to make a unique directory",
                getTargetDir().equals(getTempDir()));

        File projectDir = getTargetDir();

        List<File> files = new ArrayList<File>();
        for (String relativePath : relativePaths) {
            File file = getTestfile(relativePath);
            assertNotNull(file);
            files.add(file);
        }

        addManifestFile(projectDir);

        String result = checkLint(Collections.singletonList(projectDir));
        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }
        return result;
    }

    private void addManifestFile(File projectDir) throws IOException {
        // Ensure that there is at least a manifest file there to make it a valid project
        // as far as Lint is concerned:
        if (!new File(projectDir, "AndroidManifest.xml").exists()) {
            File manifest = new File(projectDir, "AndroidManifest.xml");
            FileWriter fw = new FileWriter(manifest);
            fw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"foo.bar2\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "</manifest>\n");
            fw.close();
        }
    }

    private StringBuilder mOutput = null;

    private static File sTempDir = null;
    protected File getTempDir() {
        if (sTempDir == null) {
            File base = new File(System.getProperty("java.io.tmpdir"));     //$NON-NLS-1$
            String os = System.getProperty("os.name");          //$NON-NLS-1$
            if (os.startsWith("Mac OS")) {                      //$NON-NLS-1$
                base = new File("/tmp");
            }
            Calendar c = Calendar.getInstance();
            String name = String.format("lintTests/%1$tF_%1$tT", c).replace(':', '-'); //$NON-NLS-1$
            File tmpDir = new File(base, name);
            if (!tmpDir.exists() && tmpDir.mkdirs()) {
                sTempDir = tmpDir;
            } else {
                sTempDir = base;
            }
        }

        return sTempDir;
    }

    protected File getTargetDir() {
        return new File(getTempDir(), getClass().getSimpleName() + "_" + getName());
    }

    private File makeTestFile(String name, String relative,
            final InputStream contents) throws IOException {
        File dir = getTargetDir();
        if (relative != null) {
            dir = new File(dir, relative);
            if (!dir.exists()) {
                boolean mkdir = dir.mkdirs();
                assertTrue(dir.getPath(), mkdir);
            }
        } else if (!dir.exists()) {
            boolean mkdir = dir.mkdirs();
            assertTrue(dir.getPath(), mkdir);
        }
        File tempFile = new File(dir, name);
        if (tempFile.exists()) {
            tempFile.delete();
        }

        Files.copy(new InputSupplier<InputStream>() {
            public InputStream getInput() throws IOException {
                return contents;
            }
        }, tempFile);

        return tempFile;
    }

    private File getTestfile(String relativePath) throws IOException {
        // Support replacing filenames and paths with a => syntax, e.g.
        //   dir/file.txt=>dir2/dir3/file2.java
        // will read dir/file.txt from the test data and write it into the target
        // directory as dir2/dir3/file2.java

        String targetPath = relativePath;
        int replaceIndex = relativePath.indexOf("=>"); //$NON-NLS-1$
        if (replaceIndex != -1) {
            // foo=>bar
            targetPath = relativePath.substring(replaceIndex + "=>".length());
            relativePath = relativePath.substring(0, replaceIndex);
        }

        String path = "data" + File.separator + relativePath; //$NON-NLS-1$
        InputStream stream =
            AbstractCheckTest.class.getResourceAsStream(path);
        assertNotNull(relativePath + " does not exist", stream);
        int index = targetPath.lastIndexOf('/');
        String relative = null;
        String name = targetPath;
        if (index != -1) {
            name = targetPath.substring(index + 1);
            relative = targetPath.substring(0, index);
        }

        return makeTestFile(name, relative, stream);
    }

    protected boolean isEnabled(Issue issue) {
        Class<? extends Detector> detectorClass = getDetector().getClass();
        if (issue.getDetectorClass() == detectorClass) {
            return true;
        }

        return false;
    }

    protected boolean includeParentPath() {
        return false;
    }

    public class TestLintClient extends Main {
        private List<String> mErrors = new ArrayList<String>();

        public List<String> getErrors() {
            return mErrors;
        }

        @Override
        public void report(Context context, Issue issue, Severity severity, Location location,
                String message, Object data) {
            StringBuilder sb = new StringBuilder();

            if (issue == IssueRegistry.LINT_ERROR) {
                return;
            }

            if (location != null && location.getFile() != null) {
                // Include parent directory for locations that have alternates, since
                // frequently the file name is the same across different resource folders
                // and we want to make sure in the tests that we're indeed passing the
                // right files in as secondary locations
                if (location.getSecondary() != null || includeParentPath()) {
                    sb.append(location.getFile().getParentFile().getName() + "/"
                            + location.getFile().getName());
                } else {
                    sb.append(location.getFile().getName());
                }

                sb.append(':');

                Position startPosition = location.getStart();
                if (startPosition != null) {
                    int line = startPosition.getLine();
                    if (line >= 0) {
                        // line is 0-based, should display 1-based
                        sb.append(Integer.toString(line + 1));
                        sb.append(':');
                    }
                }

                sb.append(' ');
            }

            if (severity == Severity.FATAL) {
                // Treat fatal errors like errors in the golden files.
                severity = Severity.ERROR;
            }
            sb.append(severity.getDescription());
            sb.append(": ");

            sb.append(message);

            if (location != null && location.getSecondary() != null) {
                location = location.getSecondary();
                while (location != null) {
                    if (location.getMessage() != null) {
                        sb.append('\n');
                        sb.append("=> ");
                        sb.append(location.getFile().getParentFile().getName() + "/"
                                + location.getFile().getName());
                        sb.append(':');
                        Position startPosition = location.getStart();
                        if (startPosition != null) {
                            int line = startPosition.getLine();
                            if (line >= 0) {
                                // line is 0-based, should display 1-based
                                sb.append(Integer.toString(line + 1));
                                sb.append(':');
                            }
                        }
                        sb.append(' ');
                        if (location.getMessage() != null) {
                            sb.append(location.getMessage());
                        }
                    }
                    location = location.getSecondary();
                }
            }
            mErrors.add(sb.toString());
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            if (exception != null) {
                exception.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            if (format != null) {
                sb.append(String.format(format, args));
            }
            if (exception != null) {
                sb.append(exception.toString());
            }
            System.err.println(sb);
        }

        @Override
        public IDomParser getDomParser() {
            return new LintCliXmlParser();
        }

        @Override
        public IJavaParser getJavaParser() {
            return new LombokParser();
        }

        @Override
        public Configuration getConfiguration(Project project) {
            return new TestConfiguration();
        }

        @Override
        public File findResource(String relativePath) {
            if (relativePath.equals("platform-tools/api/api-versions.xml")) {
                CodeSource source = getClass().getProtectionDomain().getCodeSource();
                if (source != null) {
                    URL location = source.getLocation();
                    try {
                        File dir = new File(location.toURI());
                        assertTrue(dir.getPath(), dir.exists());
                        File sdkDir = dir.getParentFile().getParentFile().getParentFile()
                                .getParentFile().getParentFile().getParentFile();
                        File file = new File(sdkDir, "development" + File.separator + "sdk"
                                + File.separator + "api-versions.xml");
                        return file;
                    } catch (URISyntaxException e) {
                        fail(e.getLocalizedMessage());
                    }
                }
            } else if (relativePath.equals("tools/support/typos-en.txt")) {
                CodeSource source = getClass().getProtectionDomain().getCodeSource();
                if (source != null) {
                    URL location = source.getLocation();
                    try {
                        File dir = new File(location.toURI());
                        assertTrue(dir.getPath(), dir.exists());
                        File sdkDir = dir.getParentFile().getParentFile().getParentFile()
                                .getParentFile().getParentFile().getParentFile();
                        File file = new File(sdkDir, "sdk" + File.separator + "files"
                                + File.separator + "typos-en.txt");
                        return file;
                    } catch (URISyntaxException e) {
                        fail(e.getLocalizedMessage());
                    }
                }
            } else {
                fail("Unit tests don't support arbitrary resource lookup yet.");
            }

            return super.findResource(relativePath);
        }
    }

    public class TestConfiguration extends Configuration {
        @Override
        public boolean isEnabled(Issue issue) {
            return AbstractCheckTest.this.isEnabled(issue);
        }

        @Override
        public void ignore(Context context, Issue issue, Location location, String message,
                Object data) {
            fail("Not supported in tests.");
        }

        @Override
        public void setSeverity(Issue issue, Severity severity) {
            fail("Not supported in tests.");
        }
    }
}
