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
package com.android.ide.eclipse.adt.internal.wizards.templates;

import static org.eclipse.core.resources.IResource.DEPTH_INFINITE;

import com.android.annotations.NonNull;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.assetstudio.AssetType;
import com.android.ide.eclipse.adt.internal.assetstudio.ConfigureAssetSetPage;
import com.android.ide.eclipse.adt.internal.assetstudio.CreateAssetSetWizardState;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectCreator;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectCreator.ProjectPopulator;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.NewXmlFileWizard;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.IWorkbench;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Wizard for creating new projects
 */
public class NewProjectWizard extends TemplateWizard {
    private static final String ATTR_COPY_ICONS = "copyIcons";     //$NON-NLS-1$
    static final String ATTR_TARGET_API = "targetApi";             //$NON-NLS-1$
    static final String ATTR_MIN_API = "minApi";                   //$NON-NLS-1$
    static final String ATTR_BUILD_API = "buildApi";               //$NON-NLS-1$
    static final String ATTR_REVISION = "revision";                //$NON-NLS-1$
    static final String ATTR_MIN_API_LEVEL = "minApiLevel";        //$NON-NLS-1$
    static final String ATTR_PACKAGE_NAME = "packageName";         //$NON-NLS-1$
    static final String ATTR_APP_TITLE = "appTitle";               //$NON-NLS-1$
    static final String CATEGORY_PROJECTS = "projects";            //$NON-NLS-1$
    static final String CATEGORY_ACTIVITIES = "activities";        //$NON-NLS-1$
    static final String CATEGORY_OTHER = "other";                  //$NON-NLS-1$

    private NewProjectPage mMainPage;
    private ActivityPage mActivityPage;
    private NewTemplatePage mTemplatePage;
    private ConfigureAssetSetPage mIconPage;
    private NewProjectWizardState mValues;
    /** The project being created */
    private IProject mProject;

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        super.init(workbench, selection);

        setWindowTitle("New Android App");

        mValues = new NewProjectWizardState();
        mMainPage = new NewProjectPage(mValues);
        mActivityPage = new ActivityPage(mValues, true, true);
    }

    @Override
    public void addPages() {
        super.addPages();
        addPage(mMainPage);
        addPage(mActivityPage);
    }

    @Override
    public IWizardPage getNextPage(IWizardPage page) {
        if (page == mMainPage) {
            if (mValues.createIcon) {
                if (mIconPage == null) {
                    // Bundle asset studio wizard to create the launcher icon
                    CreateAssetSetWizardState iconState = mValues.iconState;
                    iconState.type = AssetType.LAUNCHER;
                    iconState.outputName = "ic_launcher"; //$NON-NLS-1$
                    iconState.background = new RGB(0xff, 0xff, 0xff);
                    iconState.foreground = new RGB(0x33, 0xb6, 0xea);
                    iconState.shape = GraphicGenerator.Shape.CIRCLE;
                    iconState.trim = true;
                    iconState.padding = 10;
                    iconState.sourceType = CreateAssetSetWizardState.SourceType.CLIPART;
                    iconState.clipartName = "user.png"; //$NON-NLS-1$
                    mIconPage = new ConfigureAssetSetPage(iconState);
                    mIconPage.setTitle("Configure Launcher Icon");
                    addPage(mIconPage);
                }
                return mIconPage;
            } else {
                return mActivityPage;
            }
        }

        if (page == mIconPage) {
            return mActivityPage;
        }

        if (page == mActivityPage && mValues.createActivity) {
            if (mTemplatePage == null) {
                NewTemplateWizardState activityValues = mValues.activityValues;

                // Initialize the *default* activity name based on what we've derived
                // from the project name
                activityValues.defaults.put("activityName", mValues.activityName);

                // Hide those parameters that the template requires but that we don't want to
                // ask the users about, since we will supply these values from the rest
                // of the new project wizard.
                Set<String> hidden = activityValues.hidden;
                hidden.add(ATTR_PACKAGE_NAME);
                hidden.add(ATTR_APP_TITLE);
                hidden.add(ATTR_MIN_API);
                hidden.add(ATTR_MIN_API_LEVEL);
                hidden.add(ATTR_TARGET_API);
                hidden.add(ATTR_BUILD_API);

                mTemplatePage = new NewTemplatePage(activityValues, false);
                addPage(mTemplatePage);
            }
            mTemplatePage.setCustomMinSdk(mValues.minSdkLevel);
            return mTemplatePage;
        }

        if (page == mTemplatePage) {
            TemplateMetadata template = mValues.activityValues.getTemplateHandler().getTemplate();
            if (template != null
                    && !InstallDependencyPage.isInstalled(template.getDependencies())) {
                return getDependencyPage(template, true);
            }
        }

        if (page == mTemplatePage || !mValues.createActivity && page == mActivityPage
                || page == getDependencyPage(null, false)) {
            return null;
        }

        return super.getNextPage(page);
    }

    @Override
    public boolean canFinish() {
        // Deal with lazy creation of some pages: these may not be in the page-list yet
        // since they are constructed lazily, so consider that option here.
        if (mValues.createIcon && (mIconPage == null || !mIconPage.isPageComplete())) {
            return false;
        }
        if (mValues.createActivity && (mTemplatePage == null || !mTemplatePage.isPageComplete())) {
            return false;
        }

        // Override super behavior (which just calls isPageComplete() on each of the pages)
        // to special case the template and icon pages since we want to skip them if
        // the appropriate flags are not set.
        for (IWizardPage page : getPages()) {
            if (page == mTemplatePage && !mValues.createActivity) {
                continue;
            }
            if (page == mIconPage && !mValues.createIcon) {
                continue;
            }
            if (!page.isPageComplete()) {
                return false;
            }
        }

        return true;
    }

    @Override
    @NonNull
    protected IProject getProject() {
        return mProject;
    }

    @Override
    @NonNull
    protected List<String> getFilesToOpen() {
        return mValues.template.getFilesToOpen();
    }

    @Override
    protected List<Change> computeChanges() {
        final TemplateHandler template = mValues.template;
        // We'll be merging in an activity template, but don't create *~ backup files
        // of the merged files (such as the manifest file) in that case.
        // (NOTE: After the change from direct file manipulation to creating a list of Change
        // objects, this no longer applies - but the code is kept around a little while longer
        // in case we want to generate change objects that makes backups of merged files)
        template.setBackupMergedFiles(false);

        // Generate basic output skeleton
        Map<String, Object> paramMap = new HashMap<String, Object>();
        addProjectInfo(paramMap);

        return template.render(mProject, paramMap);
    }

    @Override
    protected boolean performFinish(final IProgressMonitor monitor)
            throws InvocationTargetException {
        try {
            IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
            String name = mValues.projectName;
            mProject = root.getProject(name);

            final TemplateHandler template = mValues.template;
            // We'll be merging in an activity template, but don't create *~ backup files
            // of the merged files (such as the manifest file) in that case.
            template.setBackupMergedFiles(false);

            ProjectPopulator projectPopulator = new ProjectPopulator() {
                @Override
                public void populate(IProject project) throws InvocationTargetException {
                    // Copy in the proguard file; templates don't provide this one.
                    // add the default proguard config
                    File libFolder = new File(AdtPlugin.getOsSdkToolsFolder(),
                            SdkConstants.FD_LIB);
                    try {
                        assert project == mProject;
                        NewProjectCreator.addLocalFile(project,
                                new File(libFolder, SdkConstants.FN_PROJECT_PROGUARD_FILE),
                                // Write ProGuard config files with the extension .pro which
                                // is what is used in the ProGuard documentation and samples
                                SdkConstants.FN_PROJECT_PROGUARD_FILE,
                                new NullProgressMonitor());
                    } catch (Exception e) {
                        AdtPlugin.log(e, null);
                    }

                    try {
                        mProject.refreshLocal(DEPTH_INFINITE, new NullProgressMonitor());
                    } catch (CoreException e) {
                        AdtPlugin.log(e, null);
                    }

                    // Render the project template
                    List<Change> changes = computeChanges();
                    if (!changes.isEmpty()) {
                        monitor.beginTask("Creating project...", changes.size());
                        try {
                            CompositeChange composite = new CompositeChange("",
                                    changes.toArray(new Change[changes.size()]));
                            composite.perform(monitor);
                        } catch (CoreException e) {
                            AdtPlugin.log(e, null);
                            throw new InvocationTargetException(e);
                        } finally {
                            monitor.done();
                        }
                    }

                    if (mValues.createIcon) { // TODO: Set progress
                        generateIcons(mProject);
                    }

                    // Render the embedded activity template template
                    if (mValues.createActivity) {
                        final TemplateHandler activityTemplate =
                                mValues.activityValues.getTemplateHandler();
                        // We'll be merging in an activity template, but don't create
                        // *~ backup files of the merged files (such as the manifest file)
                        // in that case.
                        activityTemplate.setBackupMergedFiles(false);
                        generateActivity(template, project, monitor);
                    }
                }
            };

            NewProjectCreator.create(monitor, mProject, mValues.target, projectPopulator,
                    mValues.isLibrary, mValues.projectLocation);

            try {
                mProject.refreshLocal(DEPTH_INFINITE, new NullProgressMonitor());
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            return true;
        } catch (Exception ioe) {
            AdtPlugin.log(ioe, null);
            return false;
        }
    }

    /**
     * Generate custom icons into the project based on the asset studio wizard state
     */
    private void generateIcons(final IProject newProject) {
        // Generate the custom icons
        assert mValues.createIcon;
        Map<String, Map<String, BufferedImage>> categories =
                mIconPage.generateImages(false);
        for (Map<String, BufferedImage> previews : categories.values()) {
            for (Map.Entry<String, BufferedImage> entry : previews.entrySet()) {
                String relativePath = entry.getKey();
                IPath dest = new Path(relativePath);
                IFile file = newProject.getFile(dest);

                // In case template already created icons (should remove that)
                // remove them first
                if (file.exists()) {
                    try {
                        file.delete(true, new NullProgressMonitor());
                    } catch (CoreException e) {
                        AdtPlugin.log(e, null);
                    }
                }
                NewXmlFileWizard.createWsParentDirectory(file.getParent());
                BufferedImage image = entry.getValue();

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try {
                    ImageIO.write(image, "PNG", stream); //$NON-NLS-1$
                    byte[] bytes = stream.toByteArray();
                    InputStream is = new ByteArrayInputStream(bytes);
                    file.create(is, true /*force*/, null /*progress*/);
                } catch (IOException e) {
                    AdtPlugin.log(e, null);
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }

                try {
                    file.getParent().refreshLocal(1, new NullProgressMonitor());
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }
            }
        }
    }

    /**
     * Generate the activity: Pre-populate information about the project the
     * activity needs but that we don't need to ask about when creating a new
     * project
     */
    private void generateActivity(TemplateHandler projectTemplate, IProject project,
            IProgressMonitor monitor) throws InvocationTargetException {
        assert mValues.createActivity;
        NewTemplateWizardState activityValues = mValues.activityValues;
        Map<String, Object> parameters = activityValues.parameters;

        addProjectInfo(parameters);

        TemplateHandler activityTemplate = activityValues.getTemplateHandler();
        activityTemplate.setBackupMergedFiles(false);
        List<Change> changes = activityTemplate.render(project, parameters);
        if (!changes.isEmpty()) {
            monitor.beginTask("Creating template...", changes.size());
            try {
                CompositeChange composite = new CompositeChange("",
                        changes.toArray(new Change[changes.size()]));
                composite.perform(monitor);
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
                throw new InvocationTargetException(e);
            } finally {
                monitor.done();
            }
        }

        List<String> filesToOpen = activityTemplate.getFilesToOpen();
        projectTemplate.getFilesToOpen().addAll(filesToOpen);
    }

    private void addProjectInfo(Map<String, Object> parameters) {
        parameters.put(ATTR_PACKAGE_NAME, mValues.packageName);
        parameters.put(ATTR_APP_TITLE, mValues.applicationName);
        parameters.put(ATTR_MIN_API, mValues.minSdk);
        parameters.put(ATTR_MIN_API_LEVEL, mValues.minSdkLevel);
        parameters.put(ATTR_TARGET_API, 15);
        parameters.put(ATTR_BUILD_API, mValues.target.getVersion().getApiLevel());
        parameters.put(ATTR_COPY_ICONS, !mValues.createIcon);
    }
}
