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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility methods for the {@link NlComponentTree}
 */
public final class NlTreeUtil {

  /**
   * Return the {@link DelegatedTreeEventHandler} common to the path selected in the tree.
   * <p>
   * If the selected path have several potential tree handler
   * (i.e. path with different parent were selected) returns null
   */
  @Nullable
  public static DelegatedTreeEventHandler getSelectionTreeHandler(@NotNull NlComponentTree tree) {
    TreePath[] paths = tree.getSelectionModel().getSelectionPaths();
    TreePath parentPath;
    Object parentComponent;

    // Check if the parent path exist and is a NlComponent
    if (paths.length == 0
        || (parentPath = paths[0].getParentPath()) == null
        || !((parentComponent = parentPath.getLastPathComponent()) instanceof NlComponent)) {
      return null;
    }

    // Check that all elements in the selection have the same parent
    for (int i = 1; i < paths.length; i++) {
      if (paths[i].getParentPath() != parentPath) {
        return null;
      }
    }
    // Delegate to TreeHandler
    ViewGroupHandler handler = NlComponentHelperKt.getLayoutHandler((NlComponent)parentComponent);
    if (handler != null && handler instanceof DelegatedTreeEventHandler) {
      return ((DelegatedTreeEventHandler)handler);
    }
    return null;
  }

  /**
   * Check if all paths have the same parent and returns it.
   *
   * @return The common parent of path or null if there is several parents.
   */
  @Nullable
  public static TreePath getUniqueParent(@NotNull TreePath[] paths) {
    TreePath parentPath;

    // Check if the parent path exist and is a NlComponent
    if (paths.length == 0
        || (parentPath = paths[0].getParentPath()) == null) {
      return null;
    }

    // Check that all elements in the selection have the same parent
    for (int i = 1; i < paths.length; i++) {
      if (paths[i].getParentPath() != parentPath) {
        return null;
      }
    }
    return parentPath;
  }

  /**
   * Create a new {@link DelegatedTreeEvent} and forward it to the common {@link DelegatedTreeEventHandler}
   * if any.
   *
   * @param type     {@link DelegatedTreeEvent.Type} or the event
   * @param tree     the tree on which the event occurred
   * @param receiver the receiver of the event. It can be a parent that will receive new children or a
   *                 component that will be deleted but the {@link DelegatedTreeEventHandler} can handle it
   *                 how it wants.
   * @param row      The row from {@link NlDropInsertionPicker.Result}, indicating the row before for a drop event
   *                 or -1 if it is not used
   * @return true if the event was successfully handled
   * @see #getSelectionTreeHandler(NlComponentTree)
   */
  static boolean delegateEvent(@NotNull DelegatedTreeEvent.Type type,
                               @NotNull NlComponentTree tree,
                               @NotNull NlComponent receiver,
                               int row) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths != null) {
      TreePath uniqueParent = getUniqueParent(paths);
      if (uniqueParent != null) {
        List<Object> draggedObject = collectComponentsFromPaths(paths);

        Object nextObject = null;
        if (row >= 0) {
          TreePath pathForRow = tree.getPathForRow(row + 1);
          if (pathForRow != null && pathForRow.getParentPath() == uniqueParent) {
            nextObject = pathForRow.getLastPathComponent();
          }
        }

        DelegatedTreeEventHandler handler = getSelectionTreeHandler(tree);
        if (handler != null) {
          return handler.handleTreeEvent(
            new DelegatedTreeEvent(tree, type, draggedObject, nextObject),
            receiver);
        }
      }
    }
    return false;
  }

  /**
   * Returns a list a of all the {@link TreePath#getLastPathComponent()} from the given paths
   *
   * @param paths the paths to collect the object from
   * @return A list of all the {@link TreePath#getLastPathComponent()}.
   */
  @NotNull
  private static List<Object> collectComponentsFromPaths(@NotNull TreePath[] paths) {
    return Arrays.stream(paths)
      .map(TreePath::getLastPathComponent)
      .collect(Collectors.toList());
  }

  /**
   * Modified dragged to keep only the elements that have no ancestor in the selection
   *
   * @param dragged the dragged element
   */
  public static Collection<NlComponent> keepOnlyAncestors(@NotNull Collection<NlComponent> dragged) {
    final Set<NlComponent> selection = Sets.newIdentityHashSet();
    selection.addAll(dragged);
    Stack<NlComponent> toTraverse = new Stack<>();
    for (NlComponent selectedElement : dragged) {
      final List<NlComponent> children = selectedElement.getChildren();
      // recursively delete children from the selection
      toTraverse.addAll(children);
      while (!toTraverse.isEmpty()) {
        // Traverse the subtree for each children
        NlComponent child = toTraverse.pop();
        toTraverse.addAll(child.getChildren());
        if (selection.contains(child)) {
          selection.remove(child);
        }
      }
    }
    return selection;
  }
}
