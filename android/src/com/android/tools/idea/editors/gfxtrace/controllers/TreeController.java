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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.renderers.TreeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import java.awt.*;

public abstract class TreeController extends Controller {
  public static final int TREE_ROW_HEIGHT = 19;

  @NotNull protected final JBLoadingPanel myLoadingPanel;
  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull protected final JBScrollPane myScrollPane = new JBScrollPane();
  @NotNull protected final Tree myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));

  public TreeController(@NotNull GfxTraceEditor editor, @NotNull String emptyText) {
    super(editor);
    myPanel.add(myScrollPane, BorderLayout.CENTER);
    myTree.setRowHeight(TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setLineStyleAngled();
    myTree.setCellRenderer(getRenderer());
    myTree.getEmptyText().setText(emptyText);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), editor.getProject());
    myLoadingPanel.add(myTree);
    myScrollPane.setViewportView(myLoadingPanel);
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
  }

  @NotNull
  protected TreeCellRenderer getRenderer() {
    return new TreeRenderer();
  }

  @Override
  public void clear() {
    myTree.setModel(null);
  }

  public void setRoot(DefaultMutableTreeNode root) {
    setModel(new DefaultTreeModel(root));
  }

  public void setModel(TreeModel model) {
    assert (ApplicationManager.getApplication().isDispatchThread());
    myTree.setModel(model);
    myLoadingPanel.stopLoading();
    myLoadingPanel.revalidate();
  }

  public TreeModel getModel() {
    return myTree.getModel();
  }
}
