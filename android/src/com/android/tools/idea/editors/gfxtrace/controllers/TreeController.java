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
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.idea.editors.gfxtrace.widgets.Tree;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class TreeController extends Controller {
  public static final int TREE_ROW_HEIGHT = JBUI.scale(19);

  @NotNull protected final LoadablePanel myLoadingPanel;
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
    myLoadingPanel = new LoadablePanel(new BorderLayout());
    myLoadingPanel.getContentLayer().add(myTree);
    myScrollPane.setViewportView(myLoadingPanel);
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
  }

  @NotNull
  protected abstract TreeCellRenderer getRenderer();

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
  }

  public TreeModel getModel() {
    return myTree.getModel();
  }

  public static void hoverHand(@NotNull Component component, @Nullable Path root, @Nullable Path followPath) {
    boolean validPath = followPath != null && followPath != Path.EMPTY;
    ActionMenu.showDescriptionInStatusBar(true, component, validPath ? getStatusBarTextFor(root, followPath) : null);
    component.setCursor(validPath ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
  }

  private static @NotNull String getStatusBarTextFor(@Nullable Path root, @NotNull Path path) {
    List<Path> pathParts = new ArrayList<>();
    while (path != null) {
      pathParts.add(path);
      path = path.getParent();
    }

    List<Path> rootParts = new ArrayList<>();
    while (root != null) {
      rootParts.add(root);
      root = root.getParent();
    }

    // If our path starts with status or atoms root, then we want to trim that from the start.
    for (int i = rootParts.size() - 1; i >= 0; i--) {
      if (pathParts.get(pathParts.size() - 1).equals(rootParts.get(i))) {
        pathParts.remove(pathParts.size() - 1);
      }
      else {
        break;
      }
    }

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(pathParts.get(pathParts.size() - 1).getSegmentString());
    for (int i = pathParts.size() - 2; i >= 0; i--) {
      pathParts.get(i).appendSegmentToPath(stringBuilder);
    }
    return stringBuilder.toString();
  }
}
