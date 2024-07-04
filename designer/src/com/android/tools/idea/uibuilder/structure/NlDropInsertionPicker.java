/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.List;

/**
 * Finds at after which row of a JTree the component being dragged will be inserted.
 */
class NlDropInsertionPicker {

  private final JTree myTree;

  /**
   * @param tree The tree used to find the insertion point
   */
  public NlDropInsertionPicker(@NotNull NlComponentTree tree) {
    this((JTree)tree);
  }

  @VisibleForTesting
  NlDropInsertionPicker(@NotNull JTree tree) {
    myTree = tree;
  }

  /**
   * Same as {@link #findInsertionPointAt(Point, List, boolean)} with ignoreMissingDependency as false.
   *
   * @see #findInsertionPointAt(Point, List, boolean)
   */
  public Result findInsertionPointAt(@NotNull Point location, @NotNull List<NlComponent> dragged) {
    return findInsertionPointAt(location, dragged, false);
  }

  /**
   * Find the {@link NlComponent} that will receive the dragged components and
   * the component before which the dragged component will be inserted
   *
   * @param location                Coordinate where to find the insertion point in the {@link JTree}
   * @param dragged                 The component being dragged
   * @param ignoreMissingDependency Indicate if dependency checking should be ignored when finding insertion point of dragged components.
   * @return an array of two ints used to know where to display the insertion point or null if
   * the components can't be inserted at the location of the event.
   * <p>The first int represents the row number of the path in the Tree after
   * which the dragged component will be inserted.</p>
   * <p> The second int represent the depth relative the path represented by the first int.
   * <ul>
   * <li>if the depth == 0, the insertion point will be inside the parent of the selected row</li>
   * <li>if the depth > 0, the insertion point will be inside the component on the selected row.</li>
   * <li>if the depth < 0, the insertion point will in one of the parent's ancestor. The level of the ancestor is defined by depth.</li>
   * <ul>
   * <li>-1 is the grand-parent path</li>
   * <li>-2 is the grand-grand-parent path</li>
   * <li>etc...</li>
   * </ul>
   * </li>
   * </ul>
   * </p>
   */
  @Nullable
  public Result findInsertionPointAt(@NotNull Point location,
                                     @NotNull List<NlComponent> dragged,
                                     boolean ignoreMissingDependency) {
    if (dragged.isEmpty()) {
      return findInsertionPointAt(location);
    }
    Result result = new Result();
    result.receiver = null;
    result.nextComponent = null;
    TreePath referencePath = myTree.getClosestPathForLocation(location.x, location.y);
    result.row = myTree.getRowForPath(referencePath);

    if (referencePath == null) {
      return null;
    }

    result.depth = 1;
    Object last = referencePath.getLastPathComponent();
    if (!(last instanceof NlComponent)) {
      return null;
    }
    NlComponent receiverComponent = (NlComponent)last;

    if (canChangeInsertionDepth(referencePath, receiverComponent)) {
      TreePath parentPath;
      Rectangle referenceBounds;

      // This allows the user to select the previous ancestor by moving
      // the cursor to the left only if there is some ambiguity to define where
      // the insertion should be.
      // There is an ambiguity if either the user tries to insert the component after the last row
      // in between a component deeper than the one on the next row:
      // shall we insert at the component after the last leaf or after the last leaf ancestor
      // -- root
      //   |-- parent
      //       |-- child1
      //       |-- child2
      // ..................
      //       |-- potential Insertion point 1 <---
      //   |-- potential Insertion point 2     <---
      //
      while ((parentPath = referencePath.getParentPath()) != null
             && (referenceBounds = myTree.getPathBounds(referencePath)) != null
             && canSelectLowerDepth(result.row, result.depth)
             && location.x < referenceBounds.x) {
        result.depth--;
        referencePath = parentPath;
      }
      receiverComponent = (NlComponent)referencePath.getLastPathComponent();
    }

    if (canAddComponent(receiverComponent.getModel(), receiverComponent, dragged, ignoreMissingDependency)) {
      // The receiver is a ViewGroup and can accept component
      result.receiver = receiverComponent;
      if (receiverComponent.getChildCount() != 0) {
        TreePath nextPath = myTree.getPathForRow(result.row + 1);
        result.nextComponent = nextPath == null ? null : (NlComponent)nextPath.getLastPathComponent();
      }
    }
    else {
      // The receiver is not a ViewGroup or so we need to insert in the parent
      NlComponent parent = receiverComponent.getParent();
      result.depth--;
      if (parent == null) {
        result.receiver = receiverComponent;
      }
      else {
        result.receiver = parent;
        result.nextComponent = receiverComponent.getNextSibling();
        if (result.depth <= 0) {
          // If we are inserting in a parent of referencePath, we need to
          // update the insertion mark to ensure it is displayed after
          // all the descendants of reference path
          updateInsertionPointAfterLastDescendant(referencePath, result);
        }
      }

      // Now that we should be able to add the component, we do a final check.
      // It should almost never fall in this case.
      if (!canAddComponent(result.receiver.getModel(), result.receiver, dragged, ignoreMissingDependency)) {
        result.receiver = null;
        result.nextComponent = null;
        return null;
      }
    }
    return result;
  }

  /**
   * Update the result with the row of the last and deepest expanded node of receiverPath.
   *
   * This is to ensure that we show the insertion line after all the children of a component to
   * match what will happen after the insertion.
   *
   * @param receiverPath The path where the mouse is
   * @param result       The result to update with the new row and depth insertion
   */
  private void updateInsertionPointAfterLastDescendant(@NotNull TreePath receiverPath, @NotNull Result result) {
    TreePath currentPath = receiverPath;
    TreePath lastDescendantPath = currentPath;
    int lastDesendantRow = result.row;
    int currentDepth = result.depth;


    for (Enumeration<TreePath> descendants = myTree.getExpandedDescendants(currentPath);
         descendants != null && descendants.hasMoreElements();
         descendants = myTree.getExpandedDescendants(currentPath),
           currentDepth-- // descendants are not null, which means we went one level deeper
      ) {
      do {
        currentPath = descendants.nextElement();
        lastDescendantPath = currentPath;
        lastDesendantRow = Math.max(lastDesendantRow, myTree.getRowForPath(currentPath));
      }
      while (descendants.hasMoreElements());
    }

    result.row = lastDesendantRow + myTree.getModel().getChildCount(lastDescendantPath.getLastPathComponent());
    result.depth = currentDepth;
  }


  /**
   * Find the insertion point for a drag happening on non-NlComponent.
   * <p>
   * The insertion point will only be searched inside the parent of the selected path.
   *
   * @param location The location of the drop
   * @return The {@link Result} or null if the insertion cannot happen at the provided location
   */
  private Result findInsertionPointAt(@NotNull Point location) {
    TreePath path = myTree.getSelectionPath();
    TreePath parent = path.getParentPath();
    TreePath referencePath = myTree.getClosestPathForLocation(location.x, location.y);
    Result result = new Result();
    result.shouldDelegate = true;
    result.receiver = (NlComponent)parent.getLastPathComponent();
    if (referencePath == parent) {
      result.depth = 1;
      result.row = myTree.getRowForPath(referencePath);
      return result;
    }
    else if (referencePath.getParentPath() == parent) {
      result.depth = 0;
      result.row = myTree.getRowForPath(referencePath);
      return result;
    }
    return null;
  }

  /**
   * If the path is the last of its parent's child or if the path is collapsed,
   * we can allow to change the depth of the insertion
   *
   * @param path      The current path under the mouse
   * @param component The component hold by the path
   * @return true if the insertion depth can be modified
   */
  private boolean canChangeInsertionDepth(@NotNull TreePath path, @NotNull NlComponent component) {
    return component.getNextSibling() == null && myTree.getExpandedDescendants(path) == null;
  }

  private static boolean canAddComponent(@NotNull NlModel model,
                                         @NotNull NlComponent receiver,
                                         @NotNull List<NlComponent> dragged,
                                         boolean ignoreMissingDependency) {
    return model.getTreeWriter().canAddComponents(dragged, receiver, receiver.getChild(0), ignoreMissingDependency);
  }

  private boolean canSelectLowerDepth(int row, int relativeDepth) {
    return row == myTree.getRowCount() - 1 || relativeDepth > -1;
  }

  public static class Result {

    /**
     * Receiver of the dragged element
     */
    NlComponent receiver;

    /**
     * Next sibling of the dragged element
     */
    NlComponent nextComponent;

    /**
     * The depth relative the path represented by the first int.
     * <ul>
     * <li>if the depth == 0, the insertion point will be inside the parent of the selected row</li>
     * <li>if the depth > 0, the insertion point will be inside the component on the selected row.</li>
     * <li>if the depth < 0, the insertion point will in one of the parent's ancestor. The level of the ancestor is defined by depth.</li>
     * <ul>
     */
    int depth;

    /**
     * The row after which the new element will be inserted
     */
    int row;

    /**
     * If true, that means the insertion should be delegated to a {@link DelegatedTreeEventHandler}
     */
    boolean shouldDelegate = false;
  }
}
