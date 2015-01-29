/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.editors.gfxtrace.renderers.AtomTreeWideSelectionTreeUI;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.TreeUtil;
import com.android.tools.rpclib.rpc.AtomGroup;
import com.android.tools.rpclib.rpc.Hierarchy;
import com.android.tools.rpclib.schema.AtomReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

public class AtomController implements GfxController {
  @NotNull private final SimpleTree myTreeComponent;
  @NotNull private final AtomTreeRenderer myAtomTreeRenderer;
  private TreeNode myAtomTreeRoot;
  private EnumInfoCache myEnumInfoCache;

  public AtomController(@NotNull SimpleTree treeComponent) {
    myTreeComponent = treeComponent;
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

  @SuppressWarnings("unchecked")
  @NotNull
  private static Condition<Integer> getWideSelectionBackgroundCondition() {
    return Condition.TRUE;
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

    myTreeComponent.setModel(new DefaultTreeModel(myAtomTreeRoot));
    myTreeComponent.setLargeModel(true); // Set some performance optimizations for large models.
    myTreeComponent.setRowHeight(TreeUtil.TREE_ROW_HEIGHT); // Make sure our rows are constant height.
    myTreeComponent.setCellRenderer(myAtomTreeRenderer);
    boolean isWideSelection = ((WideSelectionTreeUI)myTreeComponent.getUI()).isWideSelection();
    myTreeComponent.setUI(new AtomTreeWideSelectionTreeUI(isWideSelection, getWideSelectionBackgroundCondition()));

    myTreeComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        super.mousePressed(mouseEvent);
        if (mouseEvent.getClickCount() != 1) {
          return;
        }

        TreePath path = myTreeComponent.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());
        if (path == null) {
          return;
        }

        Component interactiveComponent = myAtomTreeRenderer.getInteractiveComponent(path.getLastPathComponent());
        if (interactiveComponent == null) {
          return;
        }

        Point localPoint = SwingUtilities.convertPoint(myTreeComponent, mouseEvent.getPoint(), interactiveComponent);
        Component subComponent = interactiveComponent.getComponentAt(localPoint);
        if (subComponent != null && subComponent instanceof JLabel) {
          // TODO: It seems like the sub components are all in the same local space as the encapsulating component (AtomNode's component),
          // rather than the usual parent-child relationship). This should be verified.
          MouseEvent localMouseEvent =
            new MouseEvent((Component)mouseEvent.getSource(), mouseEvent.getID(), mouseEvent.getWhen(), mouseEvent.getModifiers(),
                           localPoint.x, localPoint.y, mouseEvent.getClickCount(), mouseEvent.isPopupTrigger(), mouseEvent.getButton());
          // Manually forward mouse events.
          subComponent.dispatchEvent(localMouseEvent);
        }
      }
    });
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
    myTreeComponent.setModel(null);
    myAtomTreeRenderer.clear();
    myAtomTreeRoot = null;
    myEnumInfoCache = null;
  }

  @Override
  public void clearCache() {
    myAtomTreeRenderer.clearCache();
    myTreeComponent.clearSelection();
  }

  private void select(@NotNull TreePath path) {
    myTreeComponent.setSelectionPath(path);
    myTreeComponent.scrollPathToVisible(path);
  }
}
