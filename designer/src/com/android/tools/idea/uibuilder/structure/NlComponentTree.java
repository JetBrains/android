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

import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Sets;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.uibuilder.property.NlPropertiesManager.UPDATE_DELAY_MSECS;
import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.INSERT_INTO;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

public class NlComponentTree extends Tree implements DesignSurfaceListener, ModelListener, SelectionListener, DataProvider,
                                                     DeleteProvider {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);

  private final AtomicBoolean mySelectionIsUpdating;
  private final MergingUpdateQueue myUpdateQueue;

  private ScreenView myScreenView;
  private NlModel myModel;
  private TreePath myInsertionPath;
  private InsertionPoint myInsertionPoint;
  private boolean mySkipWait;

  public NlComponentTree(@NotNull DesignSurface designSurface) {
    mySelectionIsUpdating = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue(
      "android.layout.structure-pane", UPDATE_DELAY_MSECS, true, null, null, null, SWING_THREAD);
    setModel(new NlComponentTreeModel());
    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    setBorder(new EmptyBorder(INSETS));
    setRootVisible(true);
    setShowsRootHandles(false);
    setToggleClickCount(2);
    ToolTipManager.sharedInstance().registerComponent(this);
    TreeUtil.installActions(this);
    createCellRenderer();
    addTreeSelectionListener(new StructurePaneSelectionListener());
    new StructureSpeedSearch(this);
    enableDnD();
    setDesignSurface(designSurface);
    addMouseListener(new StructurePaneMouseListener());
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

    updateHierarchy();
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
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof NlComponent) {
          StructureTreeDecorator.decorate(this, (NlComponent)value);
        }
      }
    };
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    setCellRenderer(renderer);
  }

  private void invalidateUI() {
    IJSwingUtilities.updateComponentTreeUI(this);
  }

  // ---- Methods for updating hierarchy while attempting to keep expanded nodes expanded ----

  private void updateHierarchy() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    setPaintBusy(true);
    myUpdateQueue.queue(new Update("updateComponentStructure") {
      @Override
      public void run() {
        try {
          mySelectionIsUpdating.set(true);

          Collection<NlComponent> components = getCollapsedComponents();
          setModel(new NlComponentTreeModel(myModel));
          collapseComponents(components);

          invalidateUI();
        }
        finally {
          setPaintBusy(false);
          mySelectionIsUpdating.set(false);
        }

        updateSelection();
      }
    });
    if (mySkipWait) {
      mySkipWait = false;
      myUpdateQueue.flush();
    }
  }

  @NotNull
  private Collection<NlComponent> getCollapsedComponents() {
    int rowCount = getRowCount();
    Collection<NlComponent> components = Sets.newHashSetWithExpectedSize(rowCount);

    for (int row = 0; row < rowCount; row++) {
      if (isCollapsed(row)) {
        NlComponent component = (NlComponent)getPathForRow(row).getLastPathComponent();

        if (component.getChildCount() != 0) {
          components.add(component);
        }
      }
    }

    return components;
  }

  private void collapseComponents(@NotNull Collection<NlComponent> components) {
    NlComponent root = (NlComponent)getModel().getRoot();

    if (root == null) {
      return;
    }

    expandAll(root);
    components.stream().forEach(component -> collapsePath(newTreePath(component)));
  }

  private void expandAll(@NotNull NlComponent parent) {
    // If all the children are leaves
    if (parent.getChildren().stream().allMatch(child -> child.getChildCount() == 0)) {
      expandPath(newTreePath(parent));
    }
    else {
      // Recurse
      parent.getChildren().stream().forEach(this::expandAll);
    }
  }

  /**
   * Normally the outline pauses for a certain delay after a model change before updating itself
   * to reflect the new hierarchy. This method can be called to skip (just) the next update delay.
   * This is used to make operations performed <b>in</b> the outline feel more immediate.
   */
  void skipNextUpdateDelay() {
    mySkipWait = true;
  }

  private void updateSelection() {
    if (!mySelectionIsUpdating.compareAndSet(false, true)) {
      return;
    }
    try {
      clearSelection();
      if (myModel != null) {
        for (NlComponent component : myModel.getSelectionModel().getSelection()) {
          addSelectionPath(newTreePath(component));
        }
      }
    }
    finally {
      mySelectionIsUpdating.set(false);
    }
  }

  @NotNull
  private static TreePath newTreePath(@NotNull NlComponent component) {
    List<NlComponent> components = new ArrayList<>();
    components.add(component);

    for (NlComponent parent = component.getParent(); parent != null; parent = parent.getParent()) {
      components.add(parent);
    }

    Collections.reverse(components);
    return new TreePath(components.toArray());
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

  public void markInsertionPoint(@Nullable TreePath path, @NotNull InsertionPoint insertionPoint) {
    if (myInsertionPath != path || myInsertionPoint != insertionPoint) {
      myInsertionPath = path;
      myInsertionPoint = insertionPoint;
      repaint();
    }
  }

  @Override
  @SuppressWarnings("EmptyMethod")
  protected void clearToggledPaths() {
    super.clearToggledPaths();
  }

  public List<NlComponent> getSelectedComponents() {
    List<NlComponent> selected = new ArrayList<>();
    TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        selected.add((NlComponent)path.getLastPathComponent());
      }
    }
    return selected;
  }

  // ---- Implemented SelectionListener ----
  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    UIUtil.invokeLaterIfNeeded(this::updateSelection);
  }

  // ---- Implemented ModelListener ----
  @Override
  public void modelChanged(@NotNull NlModel model) {
    UIUtil.invokeLaterIfNeeded(this::updateHierarchy);
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    setScreenView(screenView);
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    if (model != null) {
      modelRendered(model);
    }
  }

  private class StructurePaneMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          Object component = path.getLastPathComponent();

          if (component instanceof NlComponent) {
            // TODO: Ensure the node is selected first
            myScreenView.getSurface().getActionManager().showPopup(e, myScreenView, (NlComponent)component);
          }
        }
      }
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
      }
      finally {
        mySelectionIsUpdating.set(false);
      }
    }
  }

  private static final class StructureSpeedSearch extends TreeSpeedSearch {
    StructureSpeedSearch(@NotNull NlComponentTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (pattern == null) {
        return false;
      }

      Object component = ((TreePath)element).getLastPathComponent();
      return compare(component instanceof NlComponent ? StructureTreeDecorator.toString((NlComponent)component) : "", pattern);
    }
  }

  // ---- Implements DataProvider ----

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  // ---- Implements DeleteProvider ----

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    SelectionModel selectionModel = myScreenView.getSelectionModel();
    NlModel model = myScreenView.getModel();
    skipNextUpdateDelay();
    model.delete(selectionModel.getSelection());
  }
}
