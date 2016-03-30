/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * <p>Updates the tree nodes after the NlModel has changed. Doing that presents a few problems:
 *
 * <ul>
 *   <li>We would like the current expanded nodes to continue to appear expanded.
 *   <li>NlComponent and XmlTag instances may have been changed and can no longer be trusted.
 * </ul>
 *
 * <p>The solution used here is not elegant. The idea is to attempt to restore the visible nodes that have an id. We require:
 *
 * <ul>
 *   <li>A mapping from component id to the tree node from the previous update.
 *   <li>A set of nodes that are currently visible which is computed here from the current tree.
 * </ul>
 *
 * <p>When we find an old node for a component id we will reuse that node and make sure all parent nodes are expanded if this node was
 * visible before the update.
 *
 * <p>As a side effect we build the following maps:
 *
 * <ul>
 *   <li>A map from component id to the new tree node (for the next hierarchy update).
 *   <li>A map from component reference to the new tree node (for handling of selection changes).
 * </ul>
 */
final class HierarchyUpdater {
  private final NlComponentTree myTree;
  private final Set<DefaultMutableTreeNode> myVisibleNodes;
  private final Map<String, DefaultMutableTreeNode> myId2TempNode;

  HierarchyUpdater(@NotNull NlComponentTree tree) {
    myTree = tree;
    myVisibleNodes = new HashSet<DefaultMutableTreeNode>();
    myId2TempNode = new HashMap<String, DefaultMutableTreeNode>(tree.getIdToNode());
  }

  public void execute() {
    myTree.getIdToNode().clear();
    myTree.getComponentToNode().clear();
    TreePath rootPath = new TreePath(myTree.getModel().getRoot());
    recordVisibleNodes(rootPath);
    myTree.clearToggledPaths();

    NlModel model = myTree.getDesignerModel();
    List<NlComponent> components = model != null ? model.getComponents() : null;
    replaceChildNodes(rootPath, components);
    if (components != null && !components.isEmpty()) {
      NlComponent root = newRootComponent();
      for (NlComponent component : components) {
        root.addChild(component);
      }
      DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)rootPath.getLastPathComponent();
      rootNode.setUserObject(root);
    }
    myTree.expandPath(rootPath);
  }

  private void recordVisibleNodes(@NotNull TreePath path) {
    if (myTree.isExpanded(path)) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      for (int i = 0; i < node.getChildCount(); i++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
        recordVisibleNodes(path.pathByAddingChild(child));
        myVisibleNodes.add(child);
      }
    }
  }

  private void replaceChildNodes(@NotNull TreePath path, @Nullable List<NlComponent> subComponents) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    node.removeAllChildren();
    if (subComponents != null) {
      boolean mustExpand = false;
      for (NlComponent child : subComponents) {
        mustExpand |= addChildNode(path, child);
      }
      if (mustExpand) {
        myTree.expandPath(path);
      }
    }
  }

  private boolean addChildNode(@NotNull TreePath path, @NotNull NlComponent component) {
    String id = component.getId();
    Map<String, DefaultMutableTreeNode> idToNode = myTree.getIdToNode();
    DefaultMutableTreeNode node = null;

    if (id != null && !idToNode.containsKey(id)) {
      node = myId2TempNode.get(id);
    }
    if (node == null) {
      node = new DefaultMutableTreeNode(component);
    }
    else {
      node.setUserObject(component);
    }
    if (id != null) {
      idToNode.put(id, node);
    }
    myTree.getComponentToNode().put(component, node);
    ((DefaultMutableTreeNode)path.getLastPathComponent()).add(node);
    replaceChildNodes(path.pathByAddingChild(node), component.children);
    return myVisibleNodes.contains(node);
  }

  @NotNull
  private NlComponent newRootComponent() {
    NlModel model = myTree.getDesignerModel();
    assert model != null;

    ViewHandler handler;

    if (ResourceType.valueOf(model.getFile()).equals(ResourceType.PREFERENCE_SCREEN)) {
      handler = new PreferenceScreenViewHandler();
    }
    else {
      handler = new DeviceScreenViewHandler();
    }

    return new FakeComponent(model, handler);
  }
}
