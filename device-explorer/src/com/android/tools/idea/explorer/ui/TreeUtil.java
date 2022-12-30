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
package com.android.tools.idea.explorer.ui;

import com.intellij.ui.tree.TreePathUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TreeUtil {
  /**
   * Returns a {@link Stream}&lt;{@link TreeNode}&gt; over the children of the given {@code node}
   */
  @NotNull
  public static Stream<TreeNode> getChildren(@NotNull TreeNode node) {
    Enumeration e = node.children();
    return StreamSupport.stream(
      Spliterators.spliterator(
        new Iterator<TreeNode>() {
          @Override
          public TreeNode next() {
            return (TreeNode)e.nextElement();
          }
          @Override
          public boolean hasNext() {
            return e.hasMoreElements();
          }
        },
        node.getChildCount(), Spliterator.ORDERED), false);
  }

  /**
   * Returns the {@link @TreePath} representing the largest path, starting at the root node, common
   * to all elements of {@code treeNodes} list.
   * <p>Example: Given {@code [a, b, c, e]} and {@code [a, b, f]}, the method returns the path {@code [a, b]}.
   */
  @Nullable
  public static <V extends DefaultMutableTreeNode> TreePath getCommonPath(@NotNull List<V> treeNodes) {
    if (treeNodes.isEmpty()) {
      return null;
    }
    TreePath[] paths = treeNodes.stream()
      .map(DefaultMutableTreeNode::getPath)
      .map(TreePath::new)
      .toArray(TreePath[]::new);
    return TreePathUtil.findCommonAncestor(paths);
  }

  public static int binarySearch(@NotNull TreeNode parent, @NotNull TreeNode key, @NotNull Comparator<TreeNode> comparator) {
    return com.intellij.util.ui.tree.TreeUtil.indexedBinarySearch(parent, key, comparator);
  }

  public interface UpdateChildrenOps<T extends MutableTreeNode, U> {
    /** Returns a child node if it is of type {@link T}, or {@code null} otherwise */
    @Nullable
    T getChildNode(@NotNull T parentNode, int index);

    /** Map an entry to a TreeNode */
    @NotNull
    T mapEntry(@NotNull U entry);

    /** Compare a TreeNode and an entry */
    int compareNodeWithEntry(@NotNull T node, @NotNull U entry);

    /** (Optionally) update a given TreeNode with a new entry value */
    void updateNode(@NotNull T node, @NotNull U entry);
  }

  /**
   * <p>
   * Given a parent tree node, with or without children, and a list of entries any type "U",
   * incrementally update the children of the parent tree node so that they exactly match
   * the list of entries. After this method is called, the list of children of the parent node
   * should have the same number of elements as the list of entries, and they all should
   * be equivalent.
   *
   * <p>
   * <strong>Warning:</strong>
   * To make sure this algorithm is bounded at O(N), there is an assumption
   * that both the existing list of children and the list of new entries are sorted
   * with an <strong>equivalent ordering</strong>, as defined by the
   * {@link UpdateChildrenOps#compareNodeWithEntry} method.
   * For example, for a file explorer view, entries would typically be sorted by filenames.
   *
   * @param treeModel The model through which child nodes are added to/removed from the parent
   * @param parentNode The parent node
   * @param newEntries A list of entries of arbitrary type to map into the list of child nodes of the parent
   * @param ops User provided operations required to map and compare tree node with elements of {@code newEntries}
   * @param <T> The type of the tree nodes, must extend {@link MutableTreeNode}
   * @param <U> An arbitrary type for the new entries to map to tree nodes.
   * @return The list of tree nodes that have been created.
   */
  @NotNull
  public static <T extends MutableTreeNode, U> List<T> updateChildrenNodes(@NotNull DefaultTreeModel treeModel,
                                                                           @NotNull T parentNode,
                                                                           @NotNull List<U> newEntries,
                                                                           @NotNull UpdateChildrenOps<T, U> ops) {
    if (newEntries.isEmpty()) {
      // Special case: new list is empty, so we remove all children
      // This is more efficient that doing incremental updates
      removeAllChildren(parentNode);
      treeModel.nodeStructureChanged(parentNode);
      return new ArrayList<>();
    }
    else if (parentNode.getChildCount() == 0) {
      // Special case: no existing children, so we map all entries and add them
      // This is more efficient that doing incremental updates
      List<T> nodes = ContainerUtil.map(newEntries, ops::mapEntry);
      removeAllChildren(parentNode);
      setAllowsChildren(parentNode); // Note: Must be done *before* inserting new nodes
      nodes.forEach(x -> parentNode.insert(x, parentNode.getChildCount()));
      treeModel.nodeStructureChanged(parentNode);
      return nodes;
    }
    else {
      // Common case: We go though both list one element at a time, ensuring that the
      // list of children up to the current index is updated to match the new entries
      List<T> addedNodes = new ArrayList<>();

      int childIndex = 0;
      int childCount = parentNode.getChildCount();
      int newEntryIndex = 0;
      int newEntryCount = newEntries.size();
      while (newEntryIndex < newEntries.size() || childIndex < parentNode.getChildCount()) {
        assert childIndex == newEntryIndex;

        // We reached the end of the existing children, just add a node from the new ones
        if (childIndex >= childCount) {
          T newEntryNode = ops.mapEntry(newEntries.get(newEntryIndex));
          addedNodes.add(newEntryNode);
          setAllowsChildren(parentNode); // Note: Must be done *before* inserting new nodes
          treeModel.insertNodeInto(newEntryNode, parentNode, childIndex);
          newEntryIndex++;
          childIndex++;
          childCount++;
          continue;
        }

        // We reached the end of the new children, delete existing children from parent
        if (newEntryIndex >= newEntryCount) {
          treeModel.removeNodeFromParent((MutableTreeNode)parentNode.getChildAt(childIndex));
          childCount--;
          continue;
        }

        // Both sides have an entry.
        T childNode = ops.getChildNode(parentNode, childIndex);
        if (childNode == null) {
          // Existing tree node is not of type "T", remove it.
          treeModel.removeNodeFromParent((MutableTreeNode)parentNode.getChildAt(childIndex));
          childCount--;
          continue;
        }

        int compareResult = ops.compareNodeWithEntry(childNode, newEntries.get(newEntryIndex));
        if (compareResult == 0) {
          ops.updateNode(childNode, newEntries.get(newEntryIndex));
          treeModel.nodeChanged(childNode);
          childIndex++;
          newEntryIndex++;
          continue;
        }

        if (compareResult < 0) {
          treeModel.removeNodeFromParent((MutableTreeNode)parentNode.getChildAt(childIndex));
          childCount--;
          continue;
        }

        // There is at least one node to insert in the existing list of children
        // For example:
        //   Existing children: [a, b, e, f]
        //   Entries          : [a, b, c, d, g, h]
        //                       0  1  2  3  4  5
        //   We need to insert 2 new tree nodes [c, d] starting at index 2 in the parent node
        //   So we compute "nextIndex" as equal to "4"  (first entry >= current child node),
        //   and we insert nodes mapped from entries at index 2 to 4 (excluded)
        assert compareResult > 0;
        int nextIndex = findIndexOfNextEntry(newEntries, newEntryIndex + 1, childNode, ops);
        assert nextIndex >= newEntryIndex + 1;
        assert nextIndex <= newEntries.size();
        for (; newEntryIndex < nextIndex; newEntryIndex++) {
          T newChildNode = ops.mapEntry(newEntries.get(newEntryIndex));
          addedNodes.add(newChildNode);
          treeModel.insertNodeInto(newChildNode, parentNode, childIndex);
          childIndex++;
          childCount++;
        }
      }
      assert childIndex == newEntryIndex;
      assert childCount == newEntryCount;
      return addedNodes;
    }
  }

  /**
   * Return the index of the first entry that comes *after* (or is equal to) the
   * given tree node.
   */
  private static <T extends MutableTreeNode, U> int findIndexOfNextEntry(@NotNull List<U> entries,
                                                                         int beginIndex,
                                                                         @NotNull T treeNode,
                                                                         @NotNull UpdateChildrenOps<T, U> ops) {
    for (int i = beginIndex; i < entries.size(); i++) {
      if (ops.compareNodeWithEntry(treeNode, entries.get(i)) <= 0) {
        return i;
      }
    }
    return entries.size();
  }

  private static void removeAllChildren(@NotNull MutableTreeNode node) {
    for (int i = node.getChildCount() - 1; i >= 0; i--) {
      node.remove(i);
    }
  }

  private static void setAllowsChildren(@NotNull MutableTreeNode node) {
    if (node instanceof DefaultMutableTreeNode) {
    ((DefaultMutableTreeNode)node).setAllowsChildren(true);
    }
  }
}
