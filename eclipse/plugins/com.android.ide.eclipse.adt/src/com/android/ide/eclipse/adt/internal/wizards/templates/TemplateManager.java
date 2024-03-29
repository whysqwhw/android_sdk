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

import static com.android.ide.eclipse.adt.internal.wizards.templates.TemplateHandler.TEMPLATE_XML;
import static com.android.sdklib.SdkConstants.FD_EXTRAS;
import static com.android.sdklib.SdkConstants.FD_TEMPLATES;
import static com.android.sdklib.SdkConstants.FD_TOOLS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Handles locating templates and providing template metadata */
class TemplateManager {
    TemplateManager() {
    }

    @Nullable
    public static File getTemplateRootFolder() {
        String location = AdtPrefs.getPrefs().getOsSdkFolder();
        if (location != null) {
            File folder = new File(location, FD_TOOLS + File.separator + FD_TEMPLATES);
            if (folder.isDirectory()) {
                return folder;
            }
        }

        return null;
    }

    @Nullable
    public static File getExtraTemplateRootFolder() {
        String location = AdtPrefs.getPrefs().getOsSdkFolder();
        if (location != null) {
            File folder = new File(location, FD_EXTRAS + File.separator + FD_TEMPLATES);
            if (folder.isDirectory()) {
                return folder;
            }
        }

        return null;
    }

    @Nullable
    public static File getTemplateLocation(@NonNull File root, @NonNull String relativePath) {
        File templateRoot = getTemplateRootFolder();
        if (templateRoot != null) {
            String rootPath = root.getPath();
            File templateFile = new File(templateRoot,
                    rootPath.replace('/', File.separatorChar) + File.separator
                    + relativePath.replace('/', File.separatorChar));
            if (templateFile.exists()) {
                return templateFile;
            }
        }

        return null;
    }

    @Nullable
    public static File getTemplateLocation(@NonNull String relativePath) {
        File templateRoot = getTemplateRootFolder();
        if (templateRoot != null) {
            File templateFile = new File(templateRoot,
                    relativePath.replace('/', File.separatorChar));
            if (templateFile.exists()) {
                return templateFile;
            }
        }

        return null;

    }

    /**
     * Returns all the templates with the given prefix
     *
     * @param folder the folder prefix
     * @return the available templates
     */
    @NonNull
    List<File> getTemplates(@NonNull String folder) {
        List<File> templates = new ArrayList<File>();
        Map<String, File> templateNames = Maps.newHashMap();
        File root = getTemplateRootFolder();
        if (root != null) {
            File[] files = new File(root, folder).listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) { // Avoid .DS_Store etc
                        templates.add(file);
                        templateNames.put(file.getName(), file);
                    }
                }
            }
        }

        // Add in templates from extras/ as well.
        root = getExtraTemplateRootFolder();
        if (root != null) {
            File[] files = new File(root, folder).listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File replaces = templateNames.get(file.getName());
                        if (replaces != null) {
                            int compare = compareTemplates(replaces, file);
                            if (compare > 0) {
                                int index = templates.indexOf(replaces);
                                if (index != -1) {
                                    templates.set(index, file);
                                } else {
                                    templates.add(file);
                                }
                            }
                        } else {
                            templates.add(file);
                        }
                    }
                }
            }
        }

        // Sort by file name (not path as is File's default)
        if (templates.size() > 1) {
            Collections.sort(templates, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    return file1.getName().compareTo(file2.getName());
                }
            });
        }

        return templates;
    }

    /**
     * Compare two files, and return the one with the HIGHEST revision, and if
     * the same, most recently modified
     */
    private int compareTemplates(File file1, File file2) {
        TemplateMetadata template1 = getTemplate(file1);
        TemplateMetadata template2 = getTemplate(file2);

        if (template1 == null) {
            return 1;
        } else if (template2 == null) {
            return -1;
        } else {
            int delta = template2.getRevision() - template1.getRevision();
            if (delta == 0) {
                delta = (int) (file2.lastModified() - file1.lastModified());
            }
            return delta;
        }
    }

    /** Cache for {@link #getTemplate()} */
    private Map<File, TemplateMetadata> mTemplateMap;

    @Nullable
    public TemplateMetadata getTemplate(File templateDir) {
        if (mTemplateMap != null) {
            TemplateMetadata metadata = mTemplateMap.get(templateDir);
            if (metadata != null) {
                return metadata;
            }
        } else {
            mTemplateMap = Maps.newHashMap();
        }

        try {
            File templateFile = new File(templateDir, TEMPLATE_XML);
            if (templateFile.isFile()) {
                String xml = Files.toString(templateFile, Charsets.UTF_8);
                Document doc = DomUtilities.parseDocument(xml, true);
                if (doc != null && doc.getDocumentElement() != null) {
                    TemplateMetadata metadata = new TemplateMetadata(doc);
                    mTemplateMap.put(templateDir, metadata);
                    return metadata;
                }
            }
        } catch (IOException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }
}
