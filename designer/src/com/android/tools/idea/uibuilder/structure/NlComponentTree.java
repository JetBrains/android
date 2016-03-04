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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

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
import java.awt.dnd.DropTarget;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlPropertiesPanel.UPDATE_DELAY_MSECS;
import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.INSERT_INTO;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

public class NlComponentTree extends Tree implements DesignSurfaceListener, ModelListener, SelectionListener {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);

  private final StructureTreeDecorator myDecorator;
  private final Map<NlComponent, DefaultMutableTreeNode> myComponent2Node;
  private final Map<String, DefaultMutableTreeNode> myId2Node;
  private final AtomicBoolean mySelectionIsUpdating;
  private final MergingUpdateQueue myUpdateQueue;

  private ScreenView myScreenView;
  private NlModel myModel;
  private boolean myWasExpanded;
  private TreePath myInsertionPath;
  private InsertionPoint myInsertionPoint;

  public NlComponentTree(@NonNull DesignSurface designSurface) {
    myDecorator = new StructureTreeDecorator(designSurface.getProject());
    myComponent2Node = new HashMap<NlComponent, DefaultMutableTreeNode>();
    myId2Node = new HashMap<String, DefaultMutableTreeNode>();
    mySelectionIsUpdating = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue(
      "android.layout.structure-pane", UPDATE_DELAY_MSECS, true, null, null, null, SWING_THREAD);
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    setModel(treeModel);
    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    setBorder(new EmptyBorder(INSETS));
    setRootVisible(false);
    setShowsRootHandles(false);
    setToggleClickCount(1);
    ToolTipManager.sharedInstance().registerComponent(this);
    TreeUtil.installActions(this);
    createCellRenderer();
    addTreeSelectionListener(new StructurePaneSelectionListener());
//todo:    new StructureSpeedSearch(myTree);
    enableDnD();
    setDesignSurface(designSurface);
  }

  private void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TreeTransferHandler());
      setDropTarget(new DropTarget(this, new NlDropListener(this)));
    }
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    setScreenView(designSurface != null ? designSurface.getCurrentScreenView() : null);
  }

  private void setScreenView(@Nullable ScreenView screenView) {
    myScreenView = screenView;
    setModel(screenView != null ? screenView.getModel() : null);
  }

  @Nullable
  public ScreenView getScreenView() {
    return myScreenView;
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

  @Nullable
  public NlModel getDesignerModel() {
    return myModel;
  }

  public void dispose() {
    if (myModel != null) {
      myModel.removeListener(this);
      myModel.getSelectionModel().removeListener(this);
      myModel = null;
    }
    Disposer.dispose(myUpdateQueue);
  }

  private void createCellRenderer() {
    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
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
    };
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    setCellRenderer(renderer);
  }

  private void loadData() {
    updateHierarchy(true);
  }

  private void invalidateUI() {
    IJSwingUtilities.updateComponentTreeUI(this);
  }

  // ---- Methods for updating hierarchy while attempting to keep expanded nodes expanded ----

  private void updateHierarchy(final boolean firstLoad) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    setPaintBusy(true);
    myUpdateQueue.queue(new Update("updateComponentStructure") {
      @Override
      public void run() {
        try {
          mySelectionIsUpdating.set(true);
          if (firstLoad) {
            myWasExpanded = false;
            myId2Node.clear();
          }
          HierarchyUpdater updater = new HierarchyUpdater();
          updater.execute();
          expandOnce();
          invalidateUI();
        }
        finally {
          setPaintBusy(false);
          mySelectionIsUpdating.set(false);
        }
        if (firstLoad) {
          updateSelection();
        }
      }
    });
  }

  private void expandOnce() {
    if (myWasExpanded) {
      return;
    }
    final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)getModel().getRoot();
    if (rootNode.isLeaf()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        DefaultMutableTreeNode nodeToExpand = rootNode;
        NlComponent component = findComponentToExpandTo();
        if (component != null) {
          nodeToExpand = myComponent2Node.get(component);
          if (nodeToExpand == null) {
            nodeToExpand = rootNode;
          }
        }
        TreePath path = new TreePath(nodeToExpand.getPath());
        expandPath(path);
        while (path != null) {
          path = path.getParentPath();
          expandPath(path);
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
        if (!child.getTagName().equals(APP_BAR_LAYOUT) && child.getTag().getAttribute(ATTR_LAYOUT_ANCHOR, AUTO_URI) == null) {
          // If this is a NestedScrollView look inside:
          if (child.getTagName().equals(CLASS_NESTED_SCROLL_VIEW) && child.children != null && !child.children.isEmpty()) {
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
      clearSelection();
      if (myModel != null) {
        for (NlComponent component : myModel.getSelectionModel().getSelection()) {
          DefaultMutableTreeNode node = myComponent2Node.get(component);
          if (node != null) {
            TreePath path = new TreePath(node.getPath());
            expandPath(path);
            addSelectionPath(path);
          }
        }
      }
    } finally {
      mySelectionIsUpdating.set(false);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myInsertionPath != null) {
      paintInsertionPoint(g);
    }
  }

  enum InsertionPoint {
    INSERT_INTO,
    INSERT_BEFORE,
    INSERT_AFTER
  }

  private void paintInsertionPoint(Graphics g) {
    if (myInsertionPath != null) {
      Rectangle pathBounds = getPathBounds(myInsertionPath);
      if (pathBounds == null) {
        return;
      }
      int y = pathBounds.y;
      switch (myInsertionPoint) {
        case INSERT_BEFORE:
          break;
        case INSERT_AFTER:
          y += pathBounds.height;
          break;
        case INSERT_INTO:
          y += pathBounds.height / 2;
          break;
      }
      Rectangle bounds = getBounds();
      Polygon triangle = new Polygon();
      triangle.addPoint(bounds.x + 6, y);
      triangle.addPoint(bounds.x, y + 3);
      triangle.addPoint(bounds.x, y - 3);
      g.setColor(UIUtil.getTreeForeground());
      if (myInsertionPoint != INSERT_INTO) {
        g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
      }
      g.drawPolygon(triangle);
      g.fillPolygon(triangle);
    }
  }

  public void markInsertionPoint(@Nullable TreePath path, @NonNull InsertionPoint insertionPoint) {
    if (myInsertionPath != path || myInsertionPoint != insertionPoint) {
      myInsertionPath = path;
      myInsertionPoint = insertionPoint;
      repaint();
    }
  }

  public List<NlComponent> getSelectedComponents() {
    List<NlComponent> selected = new ArrayList<NlComponent>();
    TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        selected.add((NlComponent)node.getUserObject());
      }
    }
    return selected;
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
        updateHierarchy(false);
      }
    });
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NonNull DesignSurface surface, @NonNull List<NlComponent> newSelection) {
  }

  @Override
  public void screenChanged(@NonNull DesignSurface surface, @Nullable ScreenView screenView) {
    setScreenView(screenView);
  }

  @Override
  public void modelChanged(@NonNull DesignSurface surface, @Nullable NlModel model) {
    if (model != null) {
      modelRendered(model);
    }
  }

  /**
   * Updating the tree nodes after the model has changed presents a few problems:
   * <ul>
   *   <li>We would like the current expanded nodes to continue to appear expanded.</li>
   *   <li>NlComponent and XmlTag instances may have been changed and can no longer be trusted.</li>
   * </ul>
   * The solution used here is not elegant. The idea is to attempt to restore the visible nodes that have an id.
   * We require:
   * <ul>
   *   <li>A mapping from component id to the tree node from the previous update.</li>
   *   <li>A set of nodes that are currently visible which is computed here from the current tree.</li>
   * </ul>
   * When we find an old node for a component id we will reuse that node and make sure all parent nodes are expanded
   * if this node was visible before the update.
   * <br/>
   * As a side effect we build the following maps:
   * <ul>
   *   <li>A map from component id to the new tree node (for the next hierarchy update).</li>
   *   <li>A map from component reference to the new tree node (for handling of selection changes).</li>
   * </ul>
   */
  private class HierarchyUpdater {
    private final Map<String, DefaultMutableTreeNode> myId2TempNode;
    private final Set<DefaultMutableTreeNode> myVisibleNodes;

    private HierarchyUpdater() {
      myId2TempNode = new HashMap<String, DefaultMutableTreeNode>(myId2Node);
      myVisibleNodes = new HashSet<DefaultMutableTreeNode>();
    }

    public void execute() {
      myId2Node.clear();
      myComponent2Node.clear();
      TreePath rootPath = new TreePath(getModel().getRoot());
      recordVisibleNodes(rootPath);
      clearToggledPaths();
      List<NlComponent> components = myModel != null ? myModel.getComponents() : null;
      replaceChildNodes(rootPath, components);
      expandPath(rootPath);
    }

    private void recordVisibleNodes(@NonNull TreePath path) {
      if (isExpanded(path)) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
        for (int i=0; i<node.getChildCount(); i++) {
          DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
          recordVisibleNodes(path.pathByAddingChild(child));
          myVisibleNodes.add(child);
        }
      }
    }

    private void replaceChildNodes(@NonNull TreePath path, @Nullable List<NlComponent> subComponents) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      node.removeAllChildren();
      if (subComponents != null) {
        boolean mustExpand = false;
        for (NlComponent child : subComponents) {
          mustExpand |= addChildNode(path, child);
        }
        if (mustExpand) {
          expandPath(path);
        }
      }
    }

    private boolean addChildNode(@NonNull TreePath path, @NonNull NlComponent component) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)path.getLastPathComponent();
      DefaultMutableTreeNode node = null;
      String id = component.getId();
      if (id != null && !myId2Node.containsKey(id)) {
        node = myId2TempNode.get(id);
      }
      if (node == null) {
        node = new DefaultMutableTreeNode(component);
      } else {
        node.setUserObject(component);
      }
      if (id != null) {
        myId2Node.put(id, node);
      }
      myComponent2Node.put(component, node);
      parent.add(node);
      replaceChildNodes(path.pathByAddingChild(node), component.children);
      return myVisibleNodes.contains(node);
    }
  }

  private class StructurePaneSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
      if (!mySelectionIsUpdating.compareAndSet(false, true)) {
        return;
      }
      try {
        myModel.getSelectionModel().setSelection(getSelectedComponents());
      } finally {
        mySelectionIsUpdating.set(false);
      }
    }
  }
}
