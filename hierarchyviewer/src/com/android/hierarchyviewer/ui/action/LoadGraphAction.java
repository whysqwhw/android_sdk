/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.hierarchyviewer.ui.action;

import com.android.hierarchyviewer.ui.Workspace;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.Toolkit;

public class LoadGraphAction extends BackgroundAction {
    public static final String ACTION_NAME = "loadGraph";
    private Workspace mWorkspace;

    public LoadGraphAction(Workspace workspace) {
        putValue(NAME, "Load View Hierarchy");
        putValue(SHORT_DESCRIPTION, "Load");
        putValue(LONG_DESCRIPTION, "Load View Hierarchy");
        putValue(MNEMONIC_KEY, KeyEvent.VK_L);
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        this.mWorkspace = workspace;
    }

    public void actionPerformed(ActionEvent e) {
        executeBackgroundTask(mWorkspace.loadGraph());
    }
}