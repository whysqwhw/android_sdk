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
import static com.android.tools.lint.detector.api.LintConstants.ATTR_ID;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_LAYOUT;
import static com.android.tools.lint.detector.api.LintConstants.DOT_XML;
import static com.android.tools.lint.detector.api.LintConstants.INCLUDE;
import static com.android.tools.lint.detector.api.LintConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.tools.lint.detector.api.LintConstants.NEW_ID_RESOURCE_PREFIX;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for duplicate ids within a layout and within an included layout
 */
public class DuplicateIdDetector extends LayoutDetector {
    private Set<String> mIds;
    private Map<File, Set<String>> mFileToIds;
    private Map<File, List<String>> mIncludes;

    // Data structures used for location collection in phase 2

    // Map from include files to include names to pairs of message and location
    // Map from file defining id, to the id to be defined, to a pair of location and message
    private Multimap<File, Multimap<String, Occurrence>> mLocations;
    private List<Occurrence> mErrors;

    /** The main issue discovered by this detector */
    public static final Issue WITHIN_LAYOUT = Issue.create(
            "DuplicateIds", //$NON-NLS-1$
            "Checks for duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise findViewById() can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            DuplicateIdDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** The main issue discovered by this detector */
    public static final Issue CROSS_LAYOUT = Issue.create(
            "DuplicateIncludedIds", //$NON-NLS-1$
            "Checks for duplicate ids across layouts that are combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if " +
            "layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or Activity#findViewById() can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            DuplicateIdDetector.class,
            Scope.ALL_RESOURCES_SCOPE);

    /** Constructs a duplicate id check */
    public DuplicateIdDetector() {
    }


    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.MENU;
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(INCLUDE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mIds = new HashSet<String>();
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // Store this layout's set of ids for full project analysis in afterCheckProject
            mFileToIds.put(context.file, mIds);

            mIds = null;
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mFileToIds = new HashMap<File, Set<String>>();
            mIncludes = new HashMap<File, List<String>>();
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // Look for duplicates
            if (mIncludes.size() > 0) {
                // Traverse all the include chains and ensure that there are no duplicates
                // across.
                if (context.isEnabled(CROSS_LAYOUT)
                        && context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                    IncludeGraph graph = new IncludeGraph(context);
                    graph.check();
                }
            }
        } else {
            assert context.getPhase() == 2;

            if (mErrors != null) {
                for (Occurrence occurrence : mErrors) {
                    //assert location != null : occurrence;
                    Location location = occurrence.location;
                    if (location == null) {
                        location = Location.create(occurrence.file);
                    } else {
                        Object clientData = location.getClientData();
                        if (clientData instanceof Node) {
                            Node node = (Node) clientData;
                            if (context.getDriver().isSuppressed(CROSS_LAYOUT, node)) {
                                continue;
                            }
                        }
                    }

                    List<Occurrence> sorted = new ArrayList<Occurrence>();
                    Occurrence curr = occurrence.next;
                    while (curr != null) {
                        sorted.add(curr);
                        curr = curr.next;
                    }
                    Collections.sort(sorted);
                    Location prev = location;
                    for (Occurrence o : sorted) {
                        if (o.location != null) {
                            prev.setSecondary(o.location);
                            prev = o.location;
                        }
                    }

                    context.report(CROSS_LAYOUT, location, occurrence.message, null);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Record include graph such that we can look for inter-layout duplicates after the
        // project has been fully checked

        String layout = element.getAttribute(ATTR_LAYOUT); // NOTE: Not in android: namespace
        if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) { // Ignore @android:layout/ layouts
            layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());

            if (context.getPhase() == 1) {
                List<String> to = mIncludes.get(context.file);
                if (to == null) {
                    to = new ArrayList<String>();
                    mIncludes.put(context.file, to);
                }
                to.add(layout);
            } else {
                assert context.getPhase() == 2;

                Collection<Multimap<String, Occurrence>> maps = mLocations.get(context.file);
                if (maps != null && maps.size() > 0) {
                    for (Multimap<String, Occurrence> map : maps) {
                        if (maps.size() > 0) {
                            Collection<Occurrence> occurrences = map.get(layout);
                            if (occurrences != null && occurrences.size() > 0) {
                                for (Occurrence occurrence : occurrences) {
                                    Location location = context.getLocation(element);
                                    location.setClientData(element);
                                    location.setMessage(occurrence.message);
                                    location.setSecondary(occurrence.location);
                                    occurrence.location = location;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        assert attribute.getName().equals(ATTR_ID) || attribute.getLocalName().equals(ATTR_ID);
        String id = attribute.getValue();
        if (context.getPhase() == 1) {
            if (mIds.contains(id)) {
                Location location = context.getLocation(attribute);

                Attr first = findIdAttribute(attribute.getOwnerDocument(), id);
                if (first != null && first != attribute) {
                    Location secondLocation = context.getLocation(first);
                    secondLocation.setMessage(String.format("%1$s originally defined here", id));
                    location.setSecondary(secondLocation);
                }

                context.report(WITHIN_LAYOUT, attribute, location,
                        String.format("Duplicate id %1$s, already defined earlier in this layout",
                                id), null);
            } else if (id.startsWith(NEW_ID_RESOURCE_PREFIX)) {
                // Skip id's on include tags
                if (attribute.getOwnerElement().getTagName().equals(INCLUDE)) {
                    return;
                }

                mIds.add(id);
            }
        } else {
            Collection<Multimap<String, Occurrence>> maps = mLocations.get(context.file);
            if (maps != null && maps.size() > 0) {
                for (Multimap<String, Occurrence> map : maps) {
                    if (maps.size() > 0) {
                        Collection<Occurrence> occurrences = map.get(id);
                        if (occurrences != null && occurrences.size() > 0) {
                            for (Occurrence occurrence : occurrences) {
                                if (context.getDriver().isSuppressed(CROSS_LAYOUT, attribute)) {
                                    return;
                                }
                                Location location = context.getLocation(attribute);
                                location.setClientData(attribute);
                                location.setMessage(occurrence.message);
                                location.setSecondary(occurrence.location);
                                occurrence.location = location;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Find the first id attribute with the given value below the given node */
    private Attr findIdAttribute(Node node, String targetValue) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Attr attribute = ((Element) node).getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            if (attribute != null && attribute.getValue().equals(targetValue)) {
                return attribute;
            }
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            Attr result = findIdAttribute(child, targetValue);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    /** Include Graph Node */
    private static class Layout {
        private File mFile;
        private List<Layout> mIncludes;
        private List<Layout> mIncludedBy;
        private Set<String> mIds;

        Layout(File file, Set<String> ids) {
            mFile = file;
            mIds = ids;
        }

        Set<String> getIds() {
            return mIds;
        }

        String getLayoutName() {
            return LintUtils.getLayoutName(mFile);
        }

        String getDisplayName() {
            return mFile.getParentFile().getName() + File.separator + mFile.getName();
        }

        void include(Layout target) {
            if (mIncludes == null) {
                mIncludes = new ArrayList<Layout>();
            }
            mIncludes.add(target);

            if (target.mIncludedBy == null) {
                target.mIncludedBy = new ArrayList<Layout>();
            }
            target.mIncludedBy.add(this);
        }

        boolean isIncluded() {
            return mIncludedBy != null && mIncludedBy.size() > 0;
        }

        File getFile() {
            return mFile;
        }

        List<Layout> getIncludes() {
            return mIncludes;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private class IncludeGraph {
        private final Context mContext;
        private final Map<File, Layout> mFileToLayout;

        public IncludeGraph(Context context) {
            mContext = context;

            // Produce a DAG of the files to be included, and compute edges to all eligible
            // includes.
            // Then visit the DAG and whenever you find a duplicate emit a warning about the
            // include path which reached it.
            mFileToLayout = new HashMap<File, Layout>(2 * mIncludes.size());
            for (File file : mIncludes.keySet()) {
                if (!mFileToLayout.containsKey(file)) {
                    mFileToLayout.put(file, new Layout(file, mFileToIds.get(file)));
                }
            }
            for (File file : mFileToIds.keySet()) {
                Set<String> ids = mFileToIds.get(file);
                if (ids != null && ids.size() > 0) {
                    if (!mFileToLayout.containsKey(file)) {
                        mFileToLayout.put(file, new Layout(file, ids));
                    }
                }
            }
            Multimap<String, Layout> nameToLayout =
                    ArrayListMultimap.create(mFileToLayout.size(), 4);
            for (File file : mFileToLayout.keySet()) {
                String name = LintUtils.getLayoutName(file);
                nameToLayout.put(name, mFileToLayout.get(file));
            }

            // Build up the DAG
            for (File file : mIncludes.keySet()) {
                Layout from = mFileToLayout.get(file);
                assert from != null : file;

                List<String> includedLayouts = mIncludes.get(file);
                for (String name : includedLayouts) {
                    Collection<Layout> layouts = nameToLayout.get(name);
                    if (layouts != null && layouts.size() > 0) {
                        if (layouts.size() == 1) {
                            from.include(layouts.iterator().next());
                        } else {
                            // See if we have an obvious match
                            File folder = from.getFile().getParentFile();
                            File candidate = new File(folder, name + DOT_XML);
                            Layout candidateLayout = mFileToLayout.get(candidate);
                            if (candidateLayout != null) {
                                from.include(candidateLayout);
                            } else if (mFileToIds.containsKey(candidate)) {
                                // We had an entry in mFileToIds, but not a layout: this
                                // means that the file exists, but had no includes or ids.
                                // This can't be a valid match: there is a layout that we know
                                // the include will pick, but it has no includes (to other layouts)
                                // and no ids, so no need to look at it
                                continue;
                            } else {
                                for (Layout to : layouts) {
                                    // Decide if the two targets are compatible
                                    if (isCompatible(from, to)) {
                                        from.include(to);
                                    }
                                }
                            }
                        }
                    } else {
                        // The layout is including some layout which has no ids or other includes
                        // so it's not relevant for a duplicate id search
                        continue;
                    }
                }
            }
        }

        /** Determine whether two layouts are compatible. They are not if they (for example)
         * specify conflicting qualifiers such as {@code -land} and {@code -port}.
         * @param from the include from
         * @param to the include to
         * @return true if the two are compatible */
        boolean isCompatible(Layout from, Layout to) {
            File fromFolder = from.mFile.getParentFile();
            File toFolder = to.mFile.getParentFile();
            if (fromFolder.equals(toFolder)) {
                return true;
            }

            String[] fromQualifiers = fromFolder.getName().split("-"); //$NON-NLS-1$
            String[] toQualifiers = toFolder.getName().split("-"); //$NON-NLS-1$

            if (isPortrait(fromQualifiers) != isPortrait(toQualifiers)) {
                return false;
            }

            return true;
        }

        private boolean isPortrait(String[] qualifiers) {
            for (String qualifier : qualifiers) {
                if (qualifier.equals("port")) { //$NON-NLS-1$
                    return true;
                } else if (qualifier.equals("land")) { //$NON-NLS-1$
                    return false;
                }
            }

            return true; // it's the default
        }

        public void check() {
            // Visit the DAG, looking for conflicts
            for (Layout layout : mFileToLayout.values()) {
                if (!layout.isIncluded()) { // Only check from "root" nodes
                    Deque<Layout> stack = new ArrayDeque<Layout>();
                    getIds(layout, stack, new HashSet<Layout>());
                }
            }
        }

        /**
         * Computes the cumulative set of ids used in a given layout. We can't
         * just depth-first-search the graph and check the set of ids
         * encountered along the way, because we need to detect when multiple
         * includes contribute the same ids. For example, if a file is included
         * more than once, that would result in duplicates.
         */
        private Set<String> getIds(Layout layout, Deque<Layout> stack, Set<Layout> seen) {
            seen.add(layout);

            Set<String> layoutIds = layout.getIds();
            List<Layout> includes = layout.getIncludes();
            if (includes != null) {
                Set<String> ids = new HashSet<String>();
                if (layoutIds != null) {
                    ids.addAll(layoutIds);
                }

                stack.push(layout);

                Multimap<String, Set<String>> nameToIds =
                        ArrayListMultimap.create(includes.size(), 4);

                for (Layout included : includes) {
                    if (seen.contains(included)) {
                        continue;
                    }
                    Set<String> includedIds = getIds(included, stack, seen);
                    if (includedIds != null) {
                        String layoutName = included.getLayoutName();

                        idCheck:
                        for (String id : includedIds) {
                            if (ids.contains(id)) {
                                Collection<Set<String>> idSets = nameToIds.get(layoutName);
                                if (idSets != null) {
                                    for (Set<String> siblingIds : idSets) {
                                        if (siblingIds.contains(id)) {
                                            // The id reference was added by a sibling,
                                            // so no need to complain (again)
                                            continue idCheck;
                                        }
                                    }
                                }

                                // Duplicate! Record location request for new phase.
                                if (mLocations == null) {
                                    mErrors = new ArrayList<Occurrence>();
                                    mLocations = ArrayListMultimap.create();
                                    mContext.getDriver().requestRepeat(DuplicateIdDetector.this,
                                            Scope.ALL_RESOURCES_SCOPE);
                                }

                                Map<Layout, Occurrence> occurrences =
                                        new HashMap<Layout, Occurrence>();
                                findId(layout, id, new ArrayDeque<Layout>(), occurrences,
                                        new HashSet<Layout>());
                                assert occurrences.size() >= 2;

                                // Stash a request to find the given include
                                Collection<Occurrence> values = occurrences.values();
                                List<Occurrence> sorted = new ArrayList<Occurrence>(values);
                                Collections.sort(sorted);
                                String msg = String.format(
                                        "Duplicate id %1$s, defined or included multiple " +
                                        "times in %2$s: %3$s",
                                        id, layout.getDisplayName(),
                                        sorted.toString());

                                // Store location request for the <include> tag
                                Occurrence primary = new Occurrence(layout.getFile(), msg, null);
                                Multimap<String, Occurrence> m = ArrayListMultimap.create();
                                m.put(layoutName, primary);
                                mLocations.put(layout.getFile(), m);
                                mErrors.add(primary);

                                Occurrence prev = primary;

                                // Now store all the included occurrences of the id
                                for (Occurrence occurrence : values) {
                                    if (occurrence.file.equals(layout.getFile())) {
                                        occurrence.message = "Defined here";
                                    } else {
                                        occurrence.message = String.format(
                                                "Defined here, included via %1$s",
                                                occurrence.includePath);
                                    }

                                    m = ArrayListMultimap.create();
                                    m.put(id, occurrence);
                                    mLocations.put(occurrence.file, m);

                                    // Link locations together
                                    prev.next = occurrence;
                                    prev = occurrence;
                                }
                            }
                            ids.add(id);
                        }

                        // Store these ids such that on a conflict, we can tell when
                        // an id was added by a single variation of this file
                        nameToIds.put(layoutName, includedIds);
                    }
                }
                Layout visited = stack.pop();
                assert visited == layout;
                return ids;
            } else {
                return layoutIds;
            }
        }

        private void findId(Layout layout, String id, Deque<Layout> stack,
                Map<Layout, Occurrence> occurrences, Set<Layout> seen) {
            seen.add(layout);

            Set<String> layoutIds = layout.getIds();
            if (layoutIds != null && layoutIds.contains(id)) {
                StringBuilder path = new StringBuilder();

                if (!stack.isEmpty()) {
                    Iterator<Layout> iterator = stack.descendingIterator();
                    while (iterator.hasNext()) {
                        path.append(iterator.next().getDisplayName());
                        path.append(" => ");
                    }
                }
                path.append(layout.getDisplayName());
                path.append(" defines ");
                path.append(id);

                assert occurrences.get(layout) == null : id + "," + layout;
                occurrences.put(layout, new Occurrence(layout.getFile(), null, path.toString()));
            }

            List<Layout> includes = layout.getIncludes();
            if (includes != null) {
                stack.push(layout);
                for (Layout included : includes) {
                    if (!seen.contains(included)) {
                        findId(included, id, stack, occurrences, seen);
                    }
                }
                Layout visited = stack.pop();
                assert visited == layout;
            }
        }
    }

    private static class Occurrence implements Comparable<Occurrence> {
        public Occurrence next;
        public File file;
        public Location location;
        public String message;
        public String includePath;

        public Occurrence(File file, String message, String includePath) {
            this.file = file;
            this.message = message;
            this.includePath = includePath;
        }

        @Override
        public String toString() {
            return includePath != null ? includePath : message;
        }

        @Override
        public int compareTo(Occurrence other) {
            // First sort by length, then sort by name
            int delta = toString().length() - other.toString().length();
            if (delta != 0) {
                return delta;
            }

            return toString().compareTo(other.toString());
        }
    }
}
