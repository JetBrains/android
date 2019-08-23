/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.mainView;

import com.intellij.ui.treeStructure.Tree;
import javax.swing.JButton;
import javax.swing.JPanel;

public class SqliteSchemaPanel {
  private JPanel myComponent;
  private Tree myTree;
  private JPanel myControlsPanel;
  private JButton myRemoveDatabaseButton;

  public JPanel getComponent() {
    return myComponent;
  }

  public Tree getTree() {
    return myTree;
  }

  public JPanel getControlsPanel() {
    return myControlsPanel;
  }

  public JButton getRemoveDatabaseButton() {
    return myRemoveDatabaseButton;
  }
}
