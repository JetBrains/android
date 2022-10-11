/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.ui;

import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;

public class DeviceMonitorPanel {
  static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  static final int TEXT_RENDERER_VERT_PADDING = 4;
  private JPanel mainComponent;
  private JPanel toolbar;
  private JComponent processTreePane;
  private Tree tree;

  private void createUIComponents() {
    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());
    tree = new Tree(treeModel) {
      @Override
      protected boolean shouldShowBusyIconIfNeeded() {
        // By default, setPaintBusy(true) is skipped if the tree component does not have the focus.
        // By overriding this method, we ensure setPaintBusy(true) is never skipped.
        return true;
      }
    };
    tree.setShowsRootHandles(true);
    tree.setRootVisible(true);
    tree.getEmptyText().setText("No debuggable process on device");
    processTreePane = new ProcessListTreeBuilder().build(tree);
  }

  @NotNull
  public Tree getTree() {
    return tree;
  }

  @NotNull
  public JPanel getComponent() {
    return mainComponent;
  }

  @NotNull
  public JPanel getToolbar() {
    return toolbar;
  }
}
