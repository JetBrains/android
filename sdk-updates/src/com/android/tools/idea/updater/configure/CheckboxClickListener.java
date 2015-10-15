/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.intellij.ui.ClickListener;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Listener for the checkboxes used in tables in {@link SdkUpdaterConfigurable} to allow ternary-state checkboxes.
 * Mostly taken from {@link CheckboxTreeBase}.
 */
class CheckboxClickListener extends ClickListener {
  TreeTableView myTreeTable;
  UpdaterTreeNode.Renderer myRenderer;

  CheckboxClickListener(TreeTableView mainComponent, final UpdaterTreeNode.Renderer renderer) {
    myTreeTable = mainComponent;
    myRenderer = renderer;
  }

  @Override
  public boolean onClick(@NotNull MouseEvent e, int clickCount) {
    Object source = e.getSource();
    if (source instanceof JComponent && !((JComponent)source).isEnabled()) {
      return false;
    }
    TreeTableTree tree = myTreeTable.getTree();
    int row = tree.getRowForLocation(e.getX(), e.getY());
    if (row < 0) {
      return false;
    }
    Rectangle rowBounds = tree.getRowBounds(row);
    myRenderer.setBounds(rowBounds);
    Rectangle checkBounds = myRenderer.myCheckbox.getBounds();
    checkBounds.setLocation(rowBounds.getLocation());

    if (checkBounds.height == 0) {
      checkBounds.height = checkBounds.width = rowBounds.height;
    }

    if (checkBounds.contains(e.getPoint())) {
      UpdaterTreeNode node = (UpdaterTreeNode)tree.getPathForRow(row).getLastPathComponent();
      node.cycleState();
      myTreeTable.repaint();
      return true;
    }

    return false;
  }
}
