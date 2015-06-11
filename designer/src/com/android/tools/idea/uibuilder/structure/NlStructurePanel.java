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
package com.android.tools.idea.uibuilder.structure;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.SdkConstants.*;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlStructurePanel extends JPanel implements LightToolWindowContent, DesignSurfaceListener, ModelListener, SelectionListener {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);

  private final DnDAwareTree myTree;
  private final StructureTreeDecorator myDecorator;
  private final Map<XmlTag, DefaultMutableTreeNode> myTag2Node;
  private final AtomicBoolean mySelectionIsUpdating;
  private final NlPropertiesPanel myPropertiesPanel;

  private NlModel myModel;
  private boolean myWasExpanded;

  public NlStructurePanel(@NonNull DesignSurface designSurface) {
    myDecorator = StructureTreeDecorator.get();
    myTree = new DnDAwareTree();
    myTag2Node = new HashMap<XmlTag, DefaultMutableTreeNode>();
    mySelectionIsUpdating = new AtomicBoolean(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myPropertiesPanel = new NlPropertiesPanel(designSurface);
    Splitter splitter = new Splitter(true, 0.4f);
    splitter.setFirstComponent(pane);
    splitter.setSecondComponent(myPropertiesPanel);
    initTree();
    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);
    setDesignSurface(designSurface);
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myPropertiesPanel.setDesignSurface(designSurface);
    setModel(designSurface != null && designSurface.getCurrentScreenView() != null
             ? designSurface.getCurrentScreenView().getModel() : null);
  }

  private void setModel(@Nullable NlModel model) {
    if (myModel != null) {
      myModel.removeListener(this);
      myModel.getSelectionModel().removeListener(this);
    }
    myModel = model;
    if (myModel != null) {
      myModel.addListener(this);
      myModel.getSelectionModel().addListener(this);
    }
    loadData();
  }

  public JComponent getPanel() {
    return this;
  }

  @Override
  public void dispose() {
    if (myModel != null) {
      myModel.removeListener(this);
      myModel.getSelectionModel().removeListener(this);
    }
  }

  @VisibleForTesting
  JTree getTree() {
    return myTree;
  }

  private void initTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree.setModel(treeModel);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myTree.setBorder(new EmptyBorder(INSETS));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setToggleClickCount(1);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    createCellRenderer(myTree);
    myTree.addTreeSelectionListener(new StructurePaneSelectionListener());
//todo:    new StructureSpeedSearch(myTree);
//todo:    enableDnD(myTree);
  }

  private void createCellRenderer(@NonNull JTree tree) {
    tree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NonNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        NlComponent component = (NlComponent)node.getUserObject();
        if (component == null) {
          return;
        }
        myDecorator.decorate(component, this, true);
      }
    });
  }

  private void loadData() {
    try {
      mySelectionIsUpdating.set(true);
      myWasExpanded = false;
      myTag2Node.clear();
      updateHierarchy();
    } finally {
      mySelectionIsUpdating.set(false);
    }
    updateSelection();
  }

  private void invalidateUI() {
    IJSwingUtilities.updateComponentTreeUI(myTree);
  }

  private void updateHierarchy() {
    DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    List<NlComponent> components = myModel != null ? myModel.getComponents() : null;
    replaceChildNodes(rootNode, components);
    expandOnce();
    invalidateUI();
  }

  private void replaceChildNodes(@NonNull DefaultMutableTreeNode node, @Nullable List<NlComponent> subComponents) {
    node.removeAllChildren();
    if (subComponents != null) {
      for (NlComponent child : subComponents) {
        node.add(makeNode(child));
      }
    }
  }

  @NonNull
  private DefaultMutableTreeNode makeNode(@NonNull NlComponent component) {
    DefaultMutableTreeNode node = myTag2Node.get(component.getTag());
    if (node == null) {
      node = new DefaultMutableTreeNode(component);
      myTag2Node.put(component.getTag(), node);
    } else {
      node.setUserObject(component);
    }
    replaceChildNodes(node, component.children);
    return node;
  }

  private void expandOnce() {
    if (myWasExpanded) {
      return;
    }
    final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    if (rootNode.isLeaf()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        DefaultMutableTreeNode nodeToExpand = rootNode;
        NlComponent component = findComponentToExpandTo();
        if (component != null) {
          nodeToExpand = myTag2Node.get(component.getTag());
          if (nodeToExpand == null) {
            nodeToExpand = rootNode;
          }
        }
        TreePath path = new TreePath(nodeToExpand.getPath());
        myTree.expandPath(path);
        while (path != null) {
          path = path.getParentPath();
          myTree.expandPath(path);
        }
        myWasExpanded = true;
      }
    });
  }

  // Find a component that it would be interesting to expand to when a new file is viewed.
  // If the file has an App Bar lookup something that may be user content.
  @Nullable
  private NlComponent findComponentToExpandTo() {
    if (myModel == null || myModel.getComponents().isEmpty()) {
      return null;
    }
    NlComponent root = myModel.getComponents().get(0);
    NlComponent childOfInterest = root;
    if (root.getTagName().equals(COORDINATOR_LAYOUT)) {
      // Find first child that is not an AppBarLayout and not anchored to anything.
      for (NlComponent child : root.getChildren()) {
        if (!child.getTagName().equals(APP_BAR_LAYOUT) &&
            child.getTag().getAttribute(ATTR_LAYOUT_ANCHOR, AUTO_URI) == null) {
          // If this is a NestedScrollView look inside:
          if (child.getTagName().equals(SdkConstants.CLASS_NESTED_SCROLL_VIEW) && child.children != null && !child.children.isEmpty()) {
            child = child.getChild(0);
          }
          childOfInterest = child;
          break;
        }
      }
    }

    if (childOfInterest == null || childOfInterest.children == null || childOfInterest.children.isEmpty()) {
      return childOfInterest;
    }
    return childOfInterest.children.get(childOfInterest.children.size() - 1);
  }

  private void updateSelection() {
    if (!mySelectionIsUpdating.compareAndSet(false, true)) {
      return;
    }
    try {
      myTree.clearSelection();
      if (myModel != null) {
        for (NlComponent component : myModel.getSelectionModel().getSelection()) {
          DefaultMutableTreeNode node = myTag2Node.get(component.getTag());
          if (node != null) {
            TreePath path = new TreePath(node.getPath());
            myTree.expandPath(path);
            myTree.addSelectionPath(path);
          }
        }
      }
    } finally {
      mySelectionIsUpdating.set(false);
    }
  }

  // ---- Implemented SelectionListener ----
  @Override
  public void selectionChanged(@NonNull SelectionModel model, @NonNull List<NlComponent> selection) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateSelection();
      }
    });
  }

  // ---- Implemented ModelListener ----
  @Override
  public void modelChanged(@NonNull NlModel model) {
  }

  @Override
  public void modelRendered(@NonNull NlModel model) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        updateHierarchy();
      }
    });
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NonNull DesignSurface surface, @NonNull List<NlComponent> newSelection) {
  }

  @Override
  public void screenChanged(@NonNull DesignSurface surface, @Nullable ScreenView screenView) {
    setModel(screenView != null ? screenView.getModel() : null);
  }

  @Override
  public void modelChanged(@NonNull DesignSurface surface, @Nullable NlModel model) {
    if (model != null) {
      modelRendered(model);
    }
  }

  private class StructurePaneSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
      if (!mySelectionIsUpdating.compareAndSet(false, true)) {
        return;
      }
      try {
        List<NlComponent> selected = new ArrayList<NlComponent>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : myTree.getSelectionPaths()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            selected.add((NlComponent)node.getUserObject());
          }
        }
        myModel.getSelectionModel().setSelection(selected);
      } finally {
        mySelectionIsUpdating.set(false);
      }
    }
  }
}
