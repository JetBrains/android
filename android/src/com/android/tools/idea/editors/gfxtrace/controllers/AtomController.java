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

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNode;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.EnumInfoCache;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.HierarchyNode;
import com.android.tools.idea.editors.gfxtrace.renderers.AtomTreeRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.idea.editors.gfxtrace.rpc.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.rpc.Hierarchy;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.*;
import java.awt.*;
import java.util.Enumeration;

public class AtomController implements GfxController {
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @NotNull private final SimpleTree myTree;
  @NotNull private final AtomTreeRenderer myAtomTreeRenderer;
  private TreeNode myAtomTreeRoot;
  private EnumInfoCache myEnumInfoCache;

  public AtomController(@NotNull Project project, @NotNull JBScrollPane scrollPane) {
    scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myTree = new SimpleTree();
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setLineStyleAngled();
    myTree.getEmptyText().setText(SELECT_CAPTURE);
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), project);
    myLoadingPanel.add(myTree);
    scrollPane.setViewportView(myLoadingPanel);
    myAtomTreeRenderer = new AtomTreeRenderer();
  }

  @NotNull
  public static TreeNode prepareData(@NotNull Hierarchy hierarchy) {
    assert (!ApplicationManager.getApplication().isDispatchThread());
    return generateAtomTree(hierarchy.getRoot());
  }

  @NotNull
  private static MutableTreeNode generateAtomTree(@NotNull AtomGroup atomGroup) {
    assert (atomGroup.getRange().getCount() > 0);

    DefaultMutableTreeNode currentNode = new DefaultMutableTreeNode();
    currentNode.setUserObject(new HierarchyNode(atomGroup));

    long lastGroupIndex = atomGroup.getRange().getFirst();
    for (AtomGroup subGroup : atomGroup.getSubGroups()) {
      long subGroupFirst = subGroup.getRange().getFirst();
      assert (subGroupFirst >= lastGroupIndex);
      if (subGroupFirst > lastGroupIndex) {
        addLeafNodes(currentNode, subGroupFirst, subGroupFirst - lastGroupIndex);
      }
      currentNode.add(generateAtomTree(subGroup));
      lastGroupIndex = subGroup.getRange().getFirst() + subGroup.getRange().getCount();
    }

    long nextSiblingStartIndex = atomGroup.getRange().getFirst() + atomGroup.getRange().getCount();
    if (nextSiblingStartIndex > lastGroupIndex) {
      addLeafNodes(currentNode, lastGroupIndex, nextSiblingStartIndex - lastGroupIndex);
    }

    return currentNode;
  }

  private static void addLeafNodes(@NotNull DefaultMutableTreeNode parentNode, long start, long count) {
    for (long i = 0, index = start; i < count; ++i, ++index) {
      AtomNode atomNode = new AtomNode(index);
      parentNode.add(new DefaultMutableTreeNode(atomNode, false));
    }
  }

  @NotNull
  public SimpleTree getTree() {
    return myTree;
  }

  @Override
  public void startLoad() {
    myTree.getEmptyText().setText("");
    myLoadingPanel.startLoading();
  }

  @Override
  public void commitData(@NotNull GfxContextChangeState state) {
    myEnumInfoCache = state.myEnumInfoCache;
    myAtomTreeRoot = state.myTreeRoot;
  }

  public void populateUi(@NotNull AtomReader atomReader) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myAtomTreeRoot != null);

    myAtomTreeRenderer.init(myEnumInfoCache, atomReader);

    myTree.setModel(new DefaultTreeModel(myAtomTreeRoot));
    myTree.setLargeModel(true); // Set some performance optimizations for large models.
    myTree.setRowHeight(TreeUtil.TREE_ROW_HEIGHT); // Make sure our rows are constant height.
    myTree.setCellRenderer(myAtomTreeRenderer);

    if (myAtomTreeRoot.getChildCount() == 0) {
      myTree.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myLoadingPanel.stopLoading();
    myLoadingPanel.revalidate();
  }

  public void selectFrame(@NotNull AtomGroup group) {
    // Search through the list for now.
    for (Enumeration it = myAtomTreeRoot.children(); it.hasMoreElements(); ) {
      TreeNode node = (TreeNode)it.nextElement();
      assert (node instanceof DefaultMutableTreeNode);

      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (!(userObject instanceof HierarchyNode)) {
        continue;
      }

      if (((HierarchyNode)userObject).isProxyFor(group)) {
        TreePath path = new TreePath(new Object[]{myAtomTreeRoot, node});
        select(path);
        break;
      }
    }
  }

  @Override
  public void clear() {
    myTree.setModel(null);
    myAtomTreeRenderer.clearState();
    myAtomTreeRoot = null;
    myEnumInfoCache = null;
  }

  @Override
  public void clearCache() {
    myTree.clearSelection();
  }

  private void select(@NotNull TreePath path) {
    myTree.setSelectionPath(path);
    myTree.scrollPathToVisible(path);
  }
}
