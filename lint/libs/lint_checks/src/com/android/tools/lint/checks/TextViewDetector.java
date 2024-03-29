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

package com.android.tools.lint.checks;

import static com.android.tools.lint.detector.api.LintConstants.ANDROID_URI;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_AUTO_TEXT;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_BUFFER_TYPE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_CAPITALIZE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_CURSOR_VISIBLE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_DIGITS;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_EDITABLE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_EDITOR_EXTRAS;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_IME_ACTION_ID;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_IME_ACTION_LABEL;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_IME_OPTIONS;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_INPUT_METHOD;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_INPUT_TYPE;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_NUMERIC;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PASSWORD;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PHONE_NUMBER;
import static com.android.tools.lint.detector.api.LintConstants.ATTR_PRIVATE_IME_OPTIONS;
import static com.android.tools.lint.detector.api.LintConstants.BUTTON;
import static com.android.tools.lint.detector.api.LintConstants.CHECKED_TEXT_VIEW;
import static com.android.tools.lint.detector.api.LintConstants.CHECK_BOX;
import static com.android.tools.lint.detector.api.LintConstants.RADIO_BUTTON;
import static com.android.tools.lint.detector.api.LintConstants.SWITCH;
import static com.android.tools.lint.detector.api.LintConstants.TEXT_VIEW;
import static com.android.tools.lint.detector.api.LintConstants.TOGGLE_BUTTON;
import static com.android.tools.lint.detector.api.LintConstants.VALUE_EDITABLE;
import static com.android.tools.lint.detector.api.LintConstants.VALUE_NONE;
import static com.android.tools.lint.detector.api.LintConstants.VALUE_TRUE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for cases where a TextView should probably be an EditText instead
 */
public class TextViewDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TextViewEdits", //$NON-NLS-1$
            "Looks for TextViews being used for input",

            "Using a <TextView> to input text is generally an error, you should be " +
            "using <EditText> instead.  EditText is a subclass of TextView, and some " +
            "of the editing support is provided by TextView, so it's possible to set " +
            "some input-related properties on a TextView. However, using a TextView " +
            "along with input attributes is usually a cut & paste error. To input " +
            "text you should be using <EditText>." +
            "\n" +
            "This check also checks subclasses of TextView, such as Button and CheckBox, " +
            "since these have the same issue: they should not be used with editable " +
            "attributes.",

            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            TextViewDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** Constructs a new {@link TextViewDetector} */
    public TextViewDetector() {
    }

    @Override
    public @NonNull Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TEXT_VIEW,
                BUTTON,
                TOGGLE_BUTTON,
                CHECK_BOX,
                RADIO_BUTTON,
                CHECKED_TEXT_VIEW,
                SWITCH
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                // Attribute not in a namespace; we only care about the android: ones
                continue;
            }

            boolean isEditAttribute = false;
            switch (name.charAt(0)) {
                case 'a': {
                    isEditAttribute = name.equals(ATTR_AUTO_TEXT);
                    break;
                }
                case 'b': {
                    isEditAttribute = name.equals(ATTR_BUFFER_TYPE) &&
                            attribute.getValue().equals(VALUE_EDITABLE);
                    break;
                }
                case 'p': {
                    isEditAttribute = name.equals(ATTR_PASSWORD)
                            || name.equals(ATTR_PHONE_NUMBER)
                            || name.equals(ATTR_PRIVATE_IME_OPTIONS);
                    break;
                }
                case 'c': {
                    isEditAttribute = name.equals(ATTR_CAPITALIZE)
                            || name.equals(ATTR_CURSOR_VISIBLE);
                    break;
                }
                case 'd': {
                    isEditAttribute = name.equals(ATTR_DIGITS);
                    break;
                }
                case 'e': {
                    if (name.equals(ATTR_EDITABLE)) {
                        isEditAttribute = attribute.getValue().equals(VALUE_TRUE);
                    } else {
                        isEditAttribute = name.equals(ATTR_EDITOR_EXTRAS);
                    }
                    break;
                }
                case 'i': {
                    if (name.equals(ATTR_INPUT_TYPE)) {
                        String value = attribute.getValue();
                        isEditAttribute = !value.isEmpty() && !value.equals(VALUE_NONE);
                    } else {
                        isEditAttribute = name.equals(ATTR_INPUT_TYPE)
                                || name.equals(ATTR_IME_OPTIONS)
                                || name.equals(ATTR_IME_ACTION_LABEL)
                                || name.equals(ATTR_IME_ACTION_ID)
                                || name.equals(ATTR_INPUT_METHOD);
                    }
                    break;
                }
                case 'n': {
                    isEditAttribute = name.equals(ATTR_NUMERIC);
                    break;
                }
            }

            if (isEditAttribute && ANDROID_URI.equals(attribute.getNamespaceURI())) {
                Location location = context.getLocation(attribute);
                String message;
                String view = element.getTagName();
                if (view.equals(TEXT_VIEW)) {
                    message = String.format(
                            "Attribute %1$s should not be used with <TextView>: " +
                            "Change element type to <EditText> ?", attribute.getName());
                } else {
                    message = String.format(
                            "Attribute %1$s should not be used with <%2$s>: " +
                            "intended for editable text widgets",
                            attribute.getName(), view);
                }
                context.report(ISSUE, attribute, location, message, null);
            }
        }
    }
}
