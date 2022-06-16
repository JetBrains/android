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

import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.common.editor.ActionUtils;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.actions.ComponentHelpAction;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.handlers.motion.MotionUtils;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTreeUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class NlComponentTree extends Tree implements DesignSurfaceListener, ModelListener, SelectionListener, Disposable,
                                                     DataProvider {
  private  final static int UPDATE_DELAY_MSECS = 250;

  private final AtomicBoolean mySelectionIsUpdating;
  private final MergingUpdateQueue myUpdateQueue;
  private final NlTreeBadgeHandler myBadgeHandler;
  private final NlVisibilityGutterPanel myVisibilityGutterPanel;

  @Nullable private NlModel myModel;
  private boolean mySkipWait;
  private int myInsertAfterRow = -1;
  private int myRelativeDepthToInsertionRow = 0;
  private Color lineColor = ColorUtil.brighter(UIUtil.getTreeSelectionBackground(true), 10);
  @Nullable private Rectangle myInsertionRowBounds;
  @Nullable private Rectangle myInsertionReceiverBounds;
  @Nullable private NlDesignSurface mySurface;

  public NlComponentTree(@NotNull Project project,
                         @Nullable NlDesignSurface designSurface,
                         NlVisibilityGutterPanel visibilityGutter) {
    mySelectionIsUpdating = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue(
      "android.layout.structure-pane", UPDATE_DELAY_MSECS, true, null, null, null, SWING_THREAD);
    myBadgeHandler = new NlTreeBadgeHandler();
    setUI(new MyUI());
    setModel(new NlComponentTreeModel());
    setDesignSurface(designSurface);
    setName("componentTree");
    setRootVisible(true);
    setToggleClickCount(2);
    setCellRenderer(new NlTreeCellRenderer(myBadgeHandler));
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        invalidateUI();
      }
    });

    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    ToolTipManager.sharedInstance().registerComponent(this);
    TreeUtil.installActions(this);
    addTreeSelectionListener(new StructurePaneSelectionListener());
    new StructureSpeedSearch(this);

    enableDnD();

    addMouseListener(new StructurePaneMouseListener());
    addMouseListener(myBadgeHandler.getBadgeMouseAdapter());
    addMouseMotionListener(myBadgeHandler.getBadgeMouseAdapter());

    ComponentHelpAction help = new ComponentHelpAction(project, () -> {
      List<NlComponent> components = getSelectedComponents();
      return !components.isEmpty() ? components.get(0).getTagName() : null;
    });
    help.registerCustomShortcutSet(KeyEvent.VK_F1, InputEvent.SHIFT_MASK, this);
    myVisibilityGutterPanel = visibilityGutter;
    addTreeExpansionListener(myVisibilityGutterPanel);
  }

  private void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TreeTransferHandler());
      setDropTarget(new DropTarget(this, new NlDropListener(this)));
    }
  }

  @Nullable
  @TestOnly
  public NlDesignSurface getDesignSurface() {
    return mySurface;
  }

  public void setDesignSurface(@Nullable NlDesignSurface designSurface) {
    if (mySurface != null) {
      mySurface.getSelectionModel().removeListener(this);
      mySurface.removeListener(this);
    }
    mySurface = designSurface;
    if (mySurface != null) {
      mySurface.getSelectionModel().addListener(this);
      mySurface.getActionManager().registerActionsShortcuts(this);
      mySurface.addListener(this);
      overrideCtrlClick();
    }
    setModel(designSurface != null ? designSurface.getModel() : null);
    myBadgeHandler.setSurface(designSurface);
  }

  /**
   * Hack to ensure that no IDEA shortcuts is called when using Ctrl+Click.
   * <p>
   * Ctrl+click has a broadly adopted meaning of multi-selecting elements.
   * In IntelliJ, it is also used to jump to the declaration of the component by default.
   * In the case of the component tree, we prefer to just add the component to the selection without jumping to
   * the XML declaration.
   */
  private void overrideCtrlClick() {
    int modifier = SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
    MouseShortcut ctrlClickShortcut = new MouseShortcut(MouseEvent.BUTTON1, modifier, 1);

    // Get all the action registered for this component
    List<AnAction> actions = ImmutableList.copyOf(ActionUtil.getActions(this));
    for (AnAction action : actions) {

      // Get all the shortcuts registered for this action and create
      // a copy them into a new list filtering Ctrl+click if it is
      // present.
      Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
      Shortcut existingShortcut = null;

      for (Shortcut shortcut : shortcuts) {
        if (shortcut.equals(ctrlClickShortcut)) {
          existingShortcut = shortcut;
          action.unregisterCustomShortcutSet(this);
          break;
        }
      }

      if (existingShortcut != null) {
        List<Shortcut> newShortcuts = new ArrayList<>(shortcuts.length - 1);
        for (Shortcut shortcut : shortcuts) {
          if (shortcut != existingShortcut) {
            newShortcuts.add(shortcut);
          }
        }
        action.registerCustomShortcutSet(new CustomShortcutSet(newShortcuts.toArray(Shortcut.EMPTY_ARRAY)), this);
      }
    }
  }

  @NotNull
  @VisibleForTesting
  public MergingUpdateQueue getUpdateQueue() {
    return myUpdateQueue;
  }

  @Nullable
  public Scene getScene() {
    return mySurface != null ? mySurface.getScene() : null;
  }

  private void setModel(@Nullable NlModel model, boolean forceUpdate) {
    if (!forceUpdate && model == myModel) {
      return;
    }
    if (myModel != null) {
      myModel.removeListener(this);
    }
    myModel = model;
    myBadgeHandler.setNlModel(myModel);
    if (myModel != null) {
      myModel.addListener(this);
    }

    updateHierarchy();
  }

  private void setModel(@Nullable NlModel model) {
    setModel(model, false);
  }

  @Nullable
  public NlModel getDesignerModel() {
    return myModel;
  }

  @Override
  public void dispose() {
    setDesignSurface(null);
    if (myModel != null) {
      myModel.removeListener(this);
      myModel = null;
    }
    ToolTipManager.sharedInstance().unregisterComponent(this);
    Disposer.dispose(myUpdateQueue);
  }

  @Override
  public void updateUI() {
    setUI(new MyUI());
    setBorder(new EmptyBorder(new JBInsets(0, 6, 0, 6)));
    lineColor = ColorUtil.brighter(UIUtil.getTreeSelectionBackground(true), 10);
    if (myBadgeHandler != null) {
      setCellRenderer(new NlTreeCellRenderer(myBadgeHandler));
    }
  }

  private void invalidateUI() {
    ((MyUI)ui).invalidateNodeSize();
    repaint();
  }

  // ---- Methods for updating hierarchy while attempting to keep expanded nodes expanded ----

  private void updateHierarchy() {
    clearInsertionPoint();
    ApplicationManager.getApplication().assertIsDispatchThread();
    myUpdateQueue.queue(new Update("updateComponentStructure") {
      @Override
      public void run() {
        try {
          if (myModel == null) {
            return;
          }
          mySelectionIsUpdating.set(true);

          // TODO b/157095734 resolve multi-selection in motion layout
          getSelectionModel().setSelectionMode(MotionUtils.getTreeSelectionModel(myModel));

          List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(NlComponentTree.this);
          Object oldRoot = treeModel.getRoot();
          setModel(new NlComponentTreeModel(myModel));
          if (oldRoot == treeModel.getRoot()) {
            TreeUtil.restoreExpandedPaths(NlComponentTree.this, expandedPaths);
          }
          else {
            TreeUtil.expandAll(NlComponentTree.this);
          }
          invalidateUI();
        }
        finally {
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

  /**
   * Normally the outline pauses for a certain delay after a model change before updating itself
   * to reflect the new hierarchy. This method can be called to skip (just) the next update delay.
   * This is used to make operations performed <b>in</b> the outline feel more immediate.
   */
  void skipNextUpdateDelay() {
    mySkipWait = true;
  }

  private void updateSelection() {
    // When updating selection it can expand collapsed paths.
    myVisibilityGutterPanel.update(NlComponentTree.this);
    if (!mySelectionIsUpdating.compareAndSet(false, true)) {
      return;
    }
    try {
      clearSelection();
      if (mySurface != null) {
        for (NlComponent component : mySurface.getSelectionModel().getSelection()) {
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
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myInsertAfterRow >= 0) {
      paintInsertionPoint((Graphics2D)g);
    }
    myBadgeHandler.paintBadges((Graphics2D)g, this);
  }

  private void paintInsertionPoint(@NotNull Graphics2D g2D) {
    if (myInsertionReceiverBounds == null || myInsertionRowBounds == null) {
      return;
    }
    RenderingHints savedHints = g2D.getRenderingHints();
    Color savedColor = g2D.getColor();
    try {
      g2D.setColor(lineColor);
      g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      paintInsertionRectangle(g2D,
                              getX(), myInsertionReceiverBounds.y,
                              getWidth(), myInsertionReceiverBounds.height);
      paintColumnLine(g2D,
                      myInsertionReceiverBounds.x, myInsertionReceiverBounds.y + myInsertionReceiverBounds.height,
                      myInsertionRowBounds.y + myInsertionRowBounds.height);
      paintInsertionLine(g2D,
                         myInsertionReceiverBounds.x, myInsertionRowBounds.y + myInsertionRowBounds.height,
                         getWidth());
    }
    finally {
      g2D.setRenderingHints(savedHints);
      g2D.setColor(savedColor);
    }
  }

  private static void paintInsertionLine(@NotNull Graphics2D g, int x, int y, int width) {
    Polygon triangle = new Polygon();
    int indicatorSize = JBUI.scale(6);
    x += JBUI.scale(6);
    triangle.addPoint(x + indicatorSize, y);
    triangle.addPoint(x, y + indicatorSize / 2);
    triangle.addPoint(x, y - indicatorSize / 2);
    Stroke stroke = g.getStroke();
    g.drawLine(x, y, x + width, y);
    g.setStroke(stroke);
    g.drawPolygon(triangle);
    g.fillPolygon(triangle);
  }

  private static void paintColumnLine(@NotNull Graphics2D g, int x, int y1, int y2) {
    Stroke stroke = g.getStroke();
    g.setStroke(NlConstants.DASHED_STROKE);
    g.drawLine(x, y1, x, y2);
    g.drawLine(x, y2, x, y2);
    g.setStroke(stroke);
  }

  private static void paintInsertionRectangle(@NotNull Graphics2D g, int x, int y, int width, int height) {
    x += JBUI.scale(1);
    y += JBUI.scale(1);
    width -= JBUI.scale(3);
    height -= JBUI.scale(4);
    g.drawRect(x, y, width, height);
  }

  /**
   * @param row           The row after which the insertion line will be displayed
   * @param relativeDepth The depth of the parent relative the row
   * @see NlDropInsertionPicker#findInsertionPointAt(Point, List)
   */
  public void markInsertionPoint(int row, int relativeDepth) {
    if (row == myInsertAfterRow && relativeDepth == myRelativeDepthToInsertionRow) {
      return;
    }

    if (row < 0) {
      clearInsertionPoint();
      return;
    }

    myInsertAfterRow = row;
    myRelativeDepthToInsertionRow = relativeDepth;
    myInsertionRowBounds = getRowBounds(myInsertAfterRow);

    // Find the bounds of the parent if the insertion row is not the receiver row
    myInsertionReceiverBounds = myInsertionRowBounds;
    if (myRelativeDepthToInsertionRow < 1) {
      TreePath receiverPath = getPathForRow(myInsertAfterRow);
      for (int i = myRelativeDepthToInsertionRow; i < 1 && receiverPath != null; i++) {
        receiverPath = receiverPath.getParentPath();
      }
      if (receiverPath != null) {
        myInsertionReceiverBounds = getPathBounds(receiverPath);
      }
    }
    repaint();
  }

  public void clearInsertionPoint() {
    myInsertionReceiverBounds = null;
    myInsertionRowBounds = null;
    myInsertAfterRow = -1;
    myRelativeDepthToInsertionRow = 0;
    repaint();
  }

  @Override
  @SuppressWarnings("EmptyMethod")
  protected void clearToggledPaths() {
    super.clearToggledPaths();
  }

  @NotNull
  public List<NlComponent> getSelectedComponents() {
    List<NlComponent> selected = new ArrayList<>();
    TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        Object last = path.getLastPathComponent();
        if (last instanceof NlComponent) {
          selected.add((NlComponent)last);
        }
      }
    }
    return selected;
  }

  // ---- Implemented SelectionListener ----
  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    UIUtil.invokeLaterIfNeeded(() -> {
      updateSelection();
      scrollPathToVisible(getSelectionPath());
    });
  }

  // ---- Implemented ModelListener ----
  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    UIUtil.invokeLaterIfNeeded(this::updateHierarchy);
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  @UiThread
  public void modelChanged(@NotNull DesignSurface<?> surface, @Nullable NlModel model) {
    setModel(model, true);
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface<?> surface, @NotNull NlComponent component) {
    return false;
  }

  private class StructurePaneMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        handleDoubleClick(e);
      }
      else {
        handlePopup(e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (mySurface != null) {
        // Clear the secondary selection whenever the component tree is pressed.
        mySurface.getSelectionModel().clearSecondary();
      }
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null && mySurface != null) {
          Object component = path.getLastPathComponent();

          if (component instanceof NlComponent) {
            // TODO: Ensure the node is selected first
            // TODO (b/151315668): extract the hardcoded value "LayoutEditor"
            ActionUtils.showPopup(mySurface, e, mySurface.getActionManager().getPopupMenuActions((NlComponent) component), "LayoutEditor");
          }
          else {
            ActionManager actionManager = ActionManager.getInstance();
            actionManager.createActionPopupMenu(
              ActionPlaces.EDITOR_POPUP,
              new DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_DELETE)))
                         .getComponent().show(e.getComponent(), e.getX(), e.getY());
          }
        }
      }
    }

    private void handleDoubleClick(@NotNull MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      TreePath path = getPathForLocation(x, y);

      if (path == null || mySurface == null) {
        return;
      }

      Object component = path.getLastPathComponent();

      if (component instanceof String) {
        NlComponent clicked = NlTreeReferencedItemHelperKt.findComponent((String) component, myModel);
        mySurface.getSelectionModel().setSelection(Arrays.asList(clicked));
        return;
      }

      if (!(component instanceof NlComponent)) {
        return;
      }

      ViewHandler handler = NlComponentHelperKt.getViewHandler((NlComponent)component);
      if (handler != null) {
        handler.onActivateInComponentTree((NlComponent)component);
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
        if (mySurface != null) {
          SelectedComponent selected = NlTreeReferencedItemHelperKt.getSelectedComponents(
            NlComponentTree.this, myModel);
          mySurface.getSelectionModel().setHighlightSelection(
            selected.getReferenced(), selected.getComponents());
          mySurface.repaint();
        }
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
      return compare(component instanceof NlComponent ? TreeSearchUtil.toString((NlComponent)component) : "", pattern);
    }
  }

  // ---- Implements DataProvider ----
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    TreePath path = getSelectionPath();
    if (path != null && !(path.getLastPathComponent() instanceof NlComponent)) {
      if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        return createNonNlComponentDeleteProvider();
      }
    }
    return mySurface == null ? null : mySurface.getData(dataId);
  }

  @NotNull
  private DeleteProvider createNonNlComponentDeleteProvider() {
    return new DeleteProvider() {
      @NotNull
      @Override
      public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void deleteElement(@NotNull DataContext dataContext) {
        deleteNonNlComponent(getSelectionPaths());
      }

      @Override
      public boolean canDeleteElement(@NotNull DataContext dataContext) {
        return true;
      }
    };
  }

  /**
   * Handle a selection of non-NlComponent (like barrier/group)
   *
   * @param selectedPath
   */
  private void deleteNonNlComponent(TreePath[] selectedPath) {
    TreePath parent = NlTreeUtil.getUniqueParent(selectedPath);
    if (parent != null) {
      Object component = parent.getLastPathComponent();
      if (component instanceof NlComponent) {
        NlTreeUtil.delegateEvent(DelegatedTreeEvent.Type.DELETE, this, ((NlComponent)component), -1);
      }
    }
  }

  boolean shouldDisplayFittedText(int index) {
    return !UIUtil.isClientPropertyTrue(this, ExpandableItemsHandler.EXPANDED_RENDERER) &&
           !getExpandableItemsHandler().getExpandedItems().contains(index);
  }

  @Override
  public boolean getShowsRootHandles() {
    // This is needed because the intelliJ Tree class ignore
    // setShowsRootHandles();
    return false;
  }

  @Override
  protected void setExpandedState(TreePath path, boolean state) {
    // We never want to collapse the root
    boolean isRoot = getRowForPath(path) == 0;
    super.setExpandedState(path, isRoot || state);
  }

  private static class MyUI extends DarculaTreeUI {
    public void invalidateNodeSize() {
      treeState.invalidateSizes();
    }
  }
}
