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

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_NAME;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_REF_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.DOT_GIF;
import static com.android.tools.lint.detector.api.LintConstants.DOT_JPG;
import static com.android.tools.lint.detector.api.LintConstants.DOT_PNG;
import static com.android.tools.lint.detector.api.LintConstants.DOT_XML;
import static com.android.tools.lint.detector.api.LintConstants.RESOURCE_CLR_STYLEABLE;
import static com.android.tools.lint.detector.api.LintConstants.RESOURCE_CLZ_ARRAY;
import static com.android.tools.lint.detector.api.LintConstants.RESOURCE_CLZ_ID;
import static com.android.tools.lint.detector.api.LintConstants.RES_FOLDER;
import static com.android.tools.lint.detector.api.LintConstants.R_ATTR_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.R_CLASS;
import static com.android.tools.lint.detector.api.LintConstants.R_ID_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.R_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.TAG_ARRAY;
import static com.android.tools.lint.detector.api.LintConstants.TAG_ITEM;
import static com.android.tools.lint.detector.api.LintConstants.TAG_RESOURCES;
import static com.android.tools.lint.detector.api.LintConstants.TAG_STRING_ARRAY;
import static com.android.tools.lint.detector.api.LintConstants.TAG_STYLE;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.ast.AstVisitor;
import lombok.ast.ClassDeclaration;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.NormalTypeBody;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;

/**
 * Finds unused resources.
 * <p>
 * Note: This detector currently performs *string* analysis to check Java files.
 * The Lint API needs an official Java AST API (or map to an existing one like
 * BCEL for bytecode analysis etc) and once it does this should be updated to
 * use it.
 */
public class UnusedResourceDetector extends ResourceXmlDetector implements Detector.JavaScanner {

    /** Unused resources (other than ids). */
    public static final Issue ISSUE = Issue.create("UnusedResources", //$NON-NLS-1$
            "Looks for unused resources",
            "Unused resources make applications larger and slow down builds.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            UnusedResourceDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES));
    /** Unused id's */
    public static final Issue ISSUE_IDS = Issue.create("UnusedIds", //$NON-NLS-1$
            "Looks for unused id's",
            "This resource id definition appears not to be needed since it is not referenced " +
            "from anywhere. Having id definitions, even if unused, is not necessarily a bad " +
            "idea since they make working on layouts and menus easier, so there is not a " +
            "strong reason to delete these.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            UnusedResourceDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES))
            .setEnabledByDefault(false);

    private Set<String> mDeclarations;
    private Set<String> mReferences;
    private Map<String, Location> mUnused;

    /**
     * Constructs a new {@link UnusedResourceDetector}
     */
    public UnusedResourceDetector() {
    }

    @Override
    public void run(@NonNull Context context) {
        assert false;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mDeclarations = new HashSet<String>(300);
            mReferences = new HashSet<String>(300);
        }
    }

    // ---- Implements JavaScanner ----

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;

        String fileName = file.getName();
        boolean isXmlFile = endsWith(fileName, DOT_XML);
        if (isXmlFile
                || endsWith(fileName, DOT_PNG)
                || endsWith(fileName, DOT_JPG)
                || endsWith(fileName, DOT_GIF)) {
            String parentName = file.getParentFile().getName();
            int dash = parentName.indexOf('-');
            String typeName = parentName.substring(0, dash == -1 ? parentName.length() : dash);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type != null && LintUtils.isFileBasedResourceType(type)) {
                String baseName = fileName.substring(0, fileName.length() - DOT_XML.length());
                String resource = R_PREFIX + typeName + '.' + baseName;
                if (context.getPhase() == 1) {
                    mDeclarations.add(resource);
                } else {
                    assert context.getPhase() == 2;
                    if (mUnused.containsKey(resource)) {
                        // Check whether this is an XML document that has a tools:ignore attribute
                        // on the document element: if so don't record it as a declaration.
                        if (isXmlFile && context instanceof XmlContext) {
                            XmlContext xmlContext = (XmlContext) context;
                            if (xmlContext.document != null
                                    && xmlContext.document.getDocumentElement() != null) {
                                Element root = xmlContext.document.getDocumentElement();
                                if (xmlContext.getDriver().isSuppressed(ISSUE, root)) {
                                    //  Also remove it from consideration such that even the
                                    // presence of this field in the R file is ignored.
                                    if (mUnused != null) {
                                        mUnused.remove(resource);
                                    }
                                    return;
                                }
                            }
                        }

                        recordLocation(resource, Location.create(file));
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mDeclarations.removeAll(mReferences);
            Set<String> unused = mDeclarations;
            mReferences = null;
            mDeclarations = null;

            // Remove styles and attributes: they may be used, analysis isn't complete for these
            List<String> styles = new ArrayList<String>();
            for (String resource : unused) {
                // R.style.x, R.styleable.x, R.attr
                if (resource.startsWith("R.style")          //$NON-NLS-1$
                        || resource.startsWith("R.attr")) { //$NON-NLS-1$
                    styles.add(resource);
                }
            }
            unused.removeAll(styles);

            // Remove id's if the user has disabled reporting issue ids
            if (unused.size() > 0 && !context.isEnabled(ISSUE_IDS)) {
                // Remove all R.id references
                List<String> ids = new ArrayList<String>();
                for (String resource : unused) {
                    if (resource.startsWith(R_ID_PREFIX)) {
                        ids.add(resource);
                    }
                }
                unused.removeAll(ids);
            }

            if (unused.size() > 0) {
                mUnused = new HashMap<String, Location>(unused.size());
                for (String resource : unused) {
                    mUnused.put(resource, null);
                }

                // Request another pass, and in the second pass we'll gather location
                // information for all declaration locations we've found
                context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
        } else {
            assert context.getPhase() == 2;

            // Report any resources that we (for some reason) could not find a declaration
            // location for
            if (mUnused.size() > 0) {
                // Fill in locations for files that we didn't encounter in other ways
                for (Map.Entry<String, Location> entry : mUnused.entrySet()) {
                    String resource = entry.getKey();
                    Location location = entry.getValue();
                    if (location != null) {
                        continue;
                    }

                    // Try to figure out the file if it's a file based resource (such as R.layout) --
                    // in that case we can figure out the filename since it has a simple mapping
                    // from the resource name (though the presence of qualifiers like -land etc
                    // makes it a little tricky if there's no base file provided)
                    int secondDot = resource.indexOf('.', 2);
                    String typeName = resource.substring(2, secondDot); // 2: Skip R.
                    ResourceType type = ResourceType.getEnum(typeName);
                    if (type != null && LintUtils.isFileBasedResourceType(type)) {
                        String name = resource.substring(secondDot + 1);

                        File res = new File(context.getProject().getDir(), RES_FOLDER);
                        File[] folders = res.listFiles();
                        if (folders != null) {
                            // Process folders in alphabetical order such that we process
                            // based folders first: we want the locations in base folder
                            // order
                            Arrays.sort(folders, new Comparator<File>() {
                                @Override
                                public int compare(File file1, File file2) {
                                    return file1.getName().compareTo(file2.getName());
                                }
                            });
                            for (File folder : folders) {
                                if (folder.getName().startsWith(typeName)) {
                                    File[] files = folder.listFiles();
                                    if (files != null) {
                                        for (File file : files) {
                                            String fileName = file.getName();
                                            if (fileName.startsWith(name)
                                                    && fileName.startsWith(".", //$NON-NLS-1$
                                                            name.length())) {
                                                recordLocation(resource, Location.create(file));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                List<String> sorted = new ArrayList<String>(mUnused.keySet());
                Collections.sort(sorted);

                for (String resource : sorted) {
                    Location location = mUnused.get(resource);
                    if (location != null) {
                        // We were prepending locations, but we want to prefer the base folders
                        location = Location.reverse(location);
                    }
                    String message = String.format("The resource %1$s appears to be unused",
                            resource);
                    Issue issue = getIssue(resource);
                    // TODO: Compute applicable node scope
                    context.report(issue, location, message, resource);
                }
            }
        }
    }

    private static Issue getIssue(String resource) {
        return resource.startsWith(R_ID_PREFIX) ? ISSUE_IDS : ISSUE;
    }

    private void recordLocation(String resource, Location location) {
        Location oldLocation = mUnused.get(resource);
        if (oldLocation != null) {
            location.setSecondary(oldLocation);
        }
        mUnused.put(resource, location);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_STYLE,
                TAG_RESOURCES,
                TAG_ARRAY,
                TAG_STRING_ARRAY
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (TAG_RESOURCES.equals(element.getTagName())) {
            for (Element item : LintUtils.getChildren(element)) {
                String name = item.getAttribute(ATTR_NAME);
                if (name.length() > 0) {
                    if (name.indexOf('.') != -1) {
                        name = name.replace('.', '_');
                    }
                    String type = item.getTagName();
                    if (type.equals(TAG_ITEM)) {
                        type = RESOURCE_CLZ_ID;
                    } else if (type.equals("declare-styleable")) {   //$NON-NLS-1$
                        type = RESOURCE_CLR_STYLEABLE;
                    } else if (type.contains("array")) {             //$NON-NLS-1$
                        // <string-array> etc
                        type = RESOURCE_CLZ_ARRAY;
                    }
                    String resource = R_PREFIX + type + '.' + name;

                    if (context.getPhase() == 1) {
                        mDeclarations.add(resource);
                        checkChildRefs(item);
                    } else {
                        assert context.getPhase() == 2;
                        if (mUnused.containsKey(resource)) {
                            if (context.getDriver().isSuppressed(getIssue(resource), item)) {
                                mUnused.remove(resource);
                                return;
                            }
                            recordLocation(resource, context.getLocation(item));
                        }
                    }
                }
            }
        } else if (mReferences != null) {
            assert TAG_STYLE.equals(element.getTagName())
                || TAG_ARRAY.equals(element.getTagName())
                || TAG_STRING_ARRAY.equals(element.getTagName());
            for (Element item : LintUtils.getChildren(element)) {
                checkChildRefs(item);
            }
        }
    }

    private void checkChildRefs(Element item) {
        // Look for ?attr/ and @dimen/foo etc references in the item children
        NodeList childNodes = item.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();

                int index = text.indexOf(ATTR_REF_PREFIX);
                if (index != -1) {
                    String name = text.substring(index + ATTR_REF_PREFIX.length()).trim();
                    mReferences.add(R_ATTR_PREFIX + name);
                } else {
                    index = text.indexOf('@');
                    if (index != -1 && text.indexOf('/', index) != -1
                            && !text.startsWith("@android:", index)) {  //$NON-NLS-1$
                        // Compute R-string, e.g. @string/foo => R.string.foo
                        String token = text.substring(index + 1).trim().replace('/', '.');
                        String r = R_PREFIX + token;
                        mReferences.add(r);
                    }
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();

        if (value.startsWith("@+") && !value.startsWith("@+android")) { //$NON-NLS-1$ //$NON-NLS-2$
            String resource = R_PREFIX + value.substring(2).replace('/', '.');
            // We already have the declarations when we scan the R file, but we're tracking
            // these here to get attributes for position info

            if (context.getPhase() == 1) {
                mDeclarations.add(resource);
            } else if (mUnused.containsKey(resource)) {
                if (context.getDriver().isSuppressed(getIssue(resource), attribute)) {
                    mUnused.remove(resource);
                    return;
                }
                recordLocation(resource, context.getLocation(attribute));
                return;
            }
        } else if (mReferences != null) {
            if (value.startsWith("@")              //$NON-NLS-1$
                    && !value.startsWith("@android:")) {  //$NON-NLS-1$
                // Compute R-string, e.g. @string/foo => R.string.foo
                String r = R_PREFIX + value.substring(1).replace('/', '.');
                mReferences.add(r);
            } else if (value.startsWith(ATTR_REF_PREFIX)) {
                mReferences.add(R_ATTR_PREFIX + value.substring(ATTR_REF_PREFIX.length()));
            }
        }

        if (attribute.getNamespaceURI() != null
                && !ANDROID_URI.equals(attribute.getNamespaceURI()) && mReferences != null) {
            mReferences.add(R_ATTR_PREFIX + attribute.getLocalName());
        }
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.SLOW;
    }

    @Override
    public List<Class<? extends lombok.ast.Node>> getApplicableNodeTypes() {
        return Collections.<Class<? extends lombok.ast.Node>>singletonList(ClassDeclaration.class);
    }

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull lombok.ast.Node node, @NonNull String type, @NonNull String name,
            boolean isFramework) {
        if (mReferences != null && !isFramework) {
            String reference = R_PREFIX + type + '.' + name;
            mReferences.add(reference);
        }
    }

    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        if (mReferences != null) {
            return new UnusedResourceVisitor();
        } else {
            // Second pass, computing resource declaration locations: No need to look at Java
            return null;
        }
    }

    // Look for references and declarations
    private class UnusedResourceVisitor extends ForwardingAstVisitor {
        @Override
        public boolean visitClassDeclaration(ClassDeclaration node) {
            // Look for declarations of R class fields and store them in
            // mDeclarations
            String description = node.getDescription();
            if (description.equals(R_CLASS)) {
                // This is an R class. We can process this class very deliberately.
                // The R class has a very specific AST format:
                // ClassDeclaration ("R")
                //    NormalTypeBody
                //        ClassDeclaration (e.g. "drawable")
                //             NormalTypeBody
                //                 VariableDeclaration
                //                     VariableDefinition (e.g. "ic_launcher")
                for (lombok.ast.Node body : node.getChildren()) {
                    if (body instanceof NormalTypeBody) {
                        for (lombok.ast.Node subclass : body.getChildren()) {
                            if (subclass instanceof ClassDeclaration) {
                                String className = ((ClassDeclaration) subclass).getDescription();
                                for (lombok.ast.Node innerBody : subclass.getChildren()) {
                                    if (innerBody instanceof NormalTypeBody) {
                                        for (lombok.ast.Node field : innerBody.getChildren()) {
                                            if (field instanceof VariableDeclaration) {
                                                for (lombok.ast.Node child : field.getChildren()) {
                                                    if (child instanceof VariableDefinition) {
                                                        VariableDefinition def =
                                                                (VariableDefinition) child;
                                                        String name = def.astVariables().first()
                                                                .astName().astValue();
                                                        String resource = R_PREFIX + className
                                                                + '.' + name;
                                                        mDeclarations.add(resource);
                                                    } // Else: It could be a comment node
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return true;
            }

            return false;
        }
    }
}
