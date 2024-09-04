/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.toolwindow;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.common.ui.properties.ChangeListener;
import com.google.idea.common.ui.properties.ObservableValue;
import com.google.idea.common.ui.properties.Property;
import com.google.idea.common.ui.templates.AbstractView;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** The view that represents the tree of the hierarchy of tasks. */
final class TasksTreeView extends AbstractView<Tree> {
  private static final Logger logger = Logger.getInstance(TasksTreeView.class);
  private final TasksTreeModel model;
  private final Disposable parentDisposable;
  private Tree tree;
  private StructureTreeModel<TasksTreeStructure> treeStructureModel;

  private final TreeSelectionListener treeSelectionListener = this::onTaskSelected;
  private final ChangeListener<Task> modelSelectedTaskListener = this::selectTask;
  private final TasksTreeProperty.InvalidationListener invalidationListener =
      this::invalidateTreeAt;
  private final TasksTreeStructure treeStructure = new TasksTreeStructure();

  TasksTreeView(TasksTreeModel model, Disposable parentDisposable) {
    this.model = model;
    this.parentDisposable = parentDisposable;
  }

  @Override
  protected Tree createComponent() {
    treeStructureModel = new StructureTreeModel<>(treeStructure, parentDisposable);
    AsyncTreeModel asyncModel = new AsyncTreeModel(treeStructureModel, parentDisposable);
    asyncModel.addTreeModelListener(new TaskNodeAutoExpandingListener());
    tree = new Tree(asyncModel);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);
    tree.setCellRenderer(new TaskDurationNodeRenderer());

    // This is required since we use animated icons in tree cell renderers:
    UIUtil.putClientProperty(tree, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, Boolean.TRUE);
    return tree;
  }

  @Override
  protected void bind() {
    getComponent().addTreeSelectionListener(treeSelectionListener);
    getComponent().addMouseListener(new PopupMenuMouseListener());
    Property<Task> selectedTaskProperty = model.selectedTaskProperty();
    selectedTaskProperty.addListener(modelSelectedTaskListener);
    selectTask(selectedTaskProperty, null, selectedTaskProperty.getValue());
    model.tasksTreeProperty().addInvalidationListener(invalidationListener);
    invalidateTreeAt(model.tasksTreeProperty().getRoot());
  }

  @Override
  protected void unbind() {
    model.tasksTreeProperty().removeInvalidationListener(invalidationListener);
    getComponent().removeTreeSelectionListener(treeSelectionListener);
    model.selectedTaskProperty().removeListener(modelSelectedTaskListener);
  }

  private void onTaskSelected(TreeSelectionEvent event) {
    TreePath selectionPath = event.getNewLeadSelectionPath();
    Object selection = selectionPath == null ? null : selectionPath.getLastPathComponent();

    if (selection instanceof LoadingNode) {
      selection = null;
    }

    if (selection != null) {
      Task task = treeNodeToTask(selection);
      model.selectedTaskProperty().setValue(task);
    }
  }

  /*
   * To avoid async update issues with how Swing handles selection callbacks on collapsing
   * multi-level trees, this method should run synchronously.
   */
  private void selectTask(
      ObservableValue<? extends Task> observable, @Nullable Task oldTask, @Nullable Task task) {
    Task currentSelectedTask =
        Optional.ofNullable(tree.getLastSelectedPathComponent())
            .map(TasksTreeView::treeNodeToTask)
            .orElse(null);
    if (Objects.equals(task, currentSelectedTask)) {
      return;
    }

    if (task == null) {
      tree.clearSelection();
      return;
    }
    Optional<TreePath> treePath = taskToTreePath(task);
    treePath.ifPresent(
        path -> UIUtil.invokeAndWaitIfNeeded(() -> TreeUtil.selectPath(tree, path, false)));
  }

  private void invalidateTreeAt(Task task) {
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () ->
                taskToTreePath(task)
                    .ifPresentOrElse(
                        path -> treeStructureModel.invalidate(path, true),
                        treeStructureModel::invalidate));
  }

  private Optional<TreePath> taskToTreePath(Task task) {
    try {
      return treeStructureModel
          .getInvoker()
          .compute(() -> taskToTreePathInternal(task))
          .get(500, MILLISECONDS);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to compute selected path", e);
    } catch (TimeoutException e) {
      logger.warn("Timed out computing tree path");
      return Optional.empty();
    }
  }

  /** Must be called on treeStructureModel's invoker thread */
  private Optional<TreePath> taskToTreePathInternal(Task task) {
    Deque<Task> taskDeque = new ArrayDeque<>();

    Optional<Task> currentTask = Optional.of(task);
    while (currentTask.isPresent()) {
      taskDeque.push(currentTask.get());
      currentTask = currentTask.get().getParent();
    }

    DefaultMutableTreeNode treeNode = objectToTreeNode(treeStructureModel.getRoot());
    TreePath resultPath = new TreePath(treeNode);

    while (!taskDeque.isEmpty()) {
      Task taskToMatch = taskDeque.pop();
      Optional<DefaultMutableTreeNode> matchingNode =
          treeStructureModel.getChildren(treeNode).stream()
              .map(TasksTreeView::objectToTreeNode)
              .filter(node -> taskToMatch.equals(treeNodeToTask(node)))
              .findFirst();
      if (matchingNode.isEmpty()) {
        return Optional.empty();
      } else {
        treeNode = matchingNode.get();
        resultPath = resultPath.pathByAddingChild(treeNode);
      }
    }
    return Optional.of(resultPath);
  }

  private class PopupMenuMouseListener extends MouseAdapter {
    @Override
    public void mouseReleased(MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
        Task selectedTask = model.selectedTaskProperty().getValue();
        if (selectedTask != null
            && selectedTask.getParent().isEmpty()
            && selectedTask.isFinished()) {
          JPopupMenu menu = new JPopupMenu("Popup Menu");
          JMenuItem removeTaskMenuItem = new JMenuItem("Remove Task");
          removeTaskMenuItem.addActionListener(
              it -> {
                model.selectedTaskProperty().setValue(null);
                TasksToolWindowService.getInstance(selectedTask.getProject())
                    .removeTask(selectedTask);
              });
          menu.add(removeTaskMenuItem);
          menu.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    }
  }

  private final class TasksTreeStructure extends AbstractTreeStructure {
    @Override
    public Task getRootElement() {
      return model.tasksTreeProperty().getRoot();
    }

    @Override
    public Task[] getChildElements(Object element) {
      List<Task> children = model.tasksTreeProperty().getChildren((Task) element);
      if (children == null) {
        return new Task[0];
      } else {
        return children.toArray(new Task[0]);
      }
    }

    @Override
    public Task getParentElement(Object element) {
      return model.tasksTreeProperty().getParent((Task) element);
    }

    @Override
    @SuppressWarnings("rawtypes") // Interface is defined by IntelliJ
    public TaskNodeDescriptor createDescriptor(
        Object element, @Nullable NodeDescriptor parentDescriptor) {
      if (!(element instanceof Task)) {
        throw new IllegalStateException("Tree structure should only contain Task instances");
      }
      if (parentDescriptor != null && !(parentDescriptor instanceof TaskNodeDescriptor)) {
        throw new IllegalStateException(
            "Expected parentDescriptor to be instance of TaskNodeDescriptor");
      }
      Task task = (Task) element;
      return new TaskNodeDescriptor(task, (TaskNodeDescriptor) parentDescriptor);
    }

    @Override
    public void commit() {}

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(Object element) {
      return false;
    }
  }

  /** Renders task duration on the right of the tree cell. Adapted from IntelliJ's Build window */
  private static class TaskDurationNodeRenderer extends NodeRenderer {
    @Nullable private String durationText;
    private Color durationColor;
    private int durationWidth = 0;
    private int durationOffset = 0;

    @Override
    public void customizeCellRenderer(
        JTree tree,
        Object treeNode,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {
      super.customizeCellRenderer(tree, treeNode, selected, expanded, leaf, row, hasFocus);

      if (treeNode instanceof LoadingNode) {
        return;
      }

      Task task = treeNodeToTask(treeNode);
      durationText = task.getDurationString().orElse(null);
      if (durationText != null) {
        FontMetrics metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()));
        durationWidth = metrics.stringWidth(durationText);
        durationOffset = metrics.getHeight() / 2; // an empty area before and after the text
        durationColor =
            selected
                ? UIUtil.getTreeSelectionForeground(hasFocus)
                : SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      UISettings.setupAntialiasing(g);
      Shape clip = null;
      int width = getWidth();
      int height = getHeight();
      if (isOpaque()) {
        // paint background for expanded row
        g.setColor(getBackground());
        g.fillRect(0, 0, width, height);
      }
      if (durationText != null && durationWidth > 0) {
        width -= durationWidth;
        width -= durationOffset;
        if (width > 0 && height > 0) {
          g.setColor(durationColor);
          g.setFont(RelativeFont.SMALL.derive(getFont()));
          g.drawString(
              durationText,
              width + durationOffset / 2,
              getTextBaseLine(g.getFontMetrics(), height));
          clip = g.getClip();
          g.clipRect(0, 0, width, height);
        }
      }

      super.paintComponent(g);
      // restore clip area if needed
      if (clip != null) {
        g.setClip(clip);
      }
    }
  }

  private class TaskNodeAutoExpandingListener implements TreeModelListener {
    @Override
    public void treeNodesInserted(TreeModelEvent e) {

      TreePath pathToParent = e.getTreePath();
      DefaultMutableTreeNode addedNode = objectToTreeNode(e.getChildren()[0]);
      Task addedTask = treeNodeToTask(addedNode);

      // Auto-expand tree to show only one level below top-level tasks (grandchildren remain
      // collapsed by default). The tree is expanded at the top-level task if it is not a leaf node
      // at the time of insertion, or if it exists in the tree as a leaf and a child task is added
      // to it.
      if (tree != null && pathToParent != null) {

        // Added task is top-level task with children
        if (model.tasksTreeProperty().isTopLevelTask(addedTask) && !addedNode.isLeaf()) {
          TreePath pathToExpand = pathToParent.pathByAddingChild(addedNode);
          if (!tree.isExpanded(pathToExpand)) {
            tree.expandPath(pathToExpand);
          }
        } else {
          // Added task is child of top-level task
          Task parentTask = treeNodeToTask(pathToParent.getLastPathComponent());
          if (model.tasksTreeProperty().isTopLevelTask(parentTask)) {
            if (!tree.isExpanded(pathToParent)) {
              tree.expandPath(pathToParent);
            }
          }
        }
      }

      // Select top-level task when added.
      if (BlazeUserSettings.getInstance().getSelectNewestChildTask()
          || model.tasksTreeProperty().isTopLevelTask(addedTask)) {
        model.selectedTaskProperty().setValue(addedTask);
      }
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {}

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {}

    @Override
    public void treeStructureChanged(TreeModelEvent e) {}
  }

  /**
   * Utility for extracting and casting of Task instances from tree nodes treated as Objects from
   * method return values or parameters
   */
  private static Task treeNodeToTask(Object node) {
    Object userObj = objectToTreeNode(node).getUserObject();
    if (userObj instanceof TaskNodeDescriptor) {
      return ((TaskNodeDescriptor) userObj).getElement();
    }
    throw new IllegalStateException(
        "Expected TaskNodeDescriptor userObject, found " + userObj.getClass().getName());
  }

  /**
   * Utility for casting DefaultMutableTreeNodes treated as Objects from method return values or
   * parameters
   */
  private static DefaultMutableTreeNode objectToTreeNode(Object node) {
    Preconditions.checkNotNull(node);
    if (node instanceof DefaultMutableTreeNode) {
      return (DefaultMutableTreeNode) node;
    }
    throw new IllegalStateException(
        "Expected DefaultMutableTreeNode, but found " + node.getClass().getName());
  }
}
