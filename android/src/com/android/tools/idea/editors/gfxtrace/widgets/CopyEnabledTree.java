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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.Transferable;
import java.util.*;

import static java.lang.Math.max;

public class CopyEnabledTree extends Tree implements DataProvider, CopyProvider {
  private final ColumnTextProvider myColumnTextProvider;

  public CopyEnabledTree(TreeModel model, @NotNull ColumnTextProvider columnTextProvider) {
    super(model);
    myColumnTextProvider = columnTextProvider;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    TreePath[] paths = getSelectionPaths();
    return paths != null && paths.length > 0;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    if (isCopyEnabled(dataContext)) {
      CopyPasteManager.getInstance().setContents(createTransferable());
    }
  }

  /**
   * Creates a text representation of the selected nodes in the tree.
   *
   * Example output:
   * state
   * └── Contexts
   *     └── 0xb79541d0
   *         └── Rasterizing
   *             ├── DepthMask         GL_TRUE
   *             ├── DepthTestFunction GL_LESS
   *             ├── DepthNear         0.000000
   *             ├── DepthFar          1.000000
   *             ├── ColorMaskRed      GL_TRUE
   *             ├── ColorMaskGreen    GL_TRUE
   *             └── ColorMaskBlue     GL_TRUE
   */
  private Transferable createTransferable() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return null;
    }

    // Create rows from all the paths.
    List<Node> roots = new ArrayList<>(paths.length);
    Map<TreePath, Node> pathToNode = new HashMap<>(paths.length);
    for (TreePath path : paths) {
      createNode(path, pathToNode, roots);
    }

    // Sort the rows into display order and measure the column widths.
    List<Integer> maxColumnWidths = new ArrayList<>(2);
    for (Node node : roots) {
      node.sort();
      node.measure(maxColumnWidths, 0);
    }

    // Print each of the roots and their children.
    StringBuffer plainBuf = new StringBuffer();
    for (Node node : roots) {
      node.print(plainBuf, maxColumnWidths);
    }

    return new TextTransferable(null, plainBuf.toString());
  }

  /**
   * Creates a new {@link Node} for the given {@link TreePath}, and any parent nodes that aren't
   * already found in {@param pathToNode}.
   * All nodes that are created are added to {@param pathToNode}.
   * Root nodes that are created are added to {@param roots}.
   *
   * @return the newly created node.
   */
  private Node createNode(TreePath path, Map<TreePath, Node> pathToNode, List<Node> roots) {
    Node node = pathToNode.getOrDefault(path, null);
    if (node != null) {
      return node;
    }

    int order = getRowForPath(path);
    node = new Node(myColumnTextProvider.getColumns(path), order);
    pathToNode.put(path, node);

    TreePath parent = path.getParentPath();
    if (parent != null) {
      createNode(parent, pathToNode, roots).myChildren.add(node);
    } else {
      roots.add(node);
    }
    return node;
  }

  public interface ColumnTextProvider {
    @NotNull
    String[] getColumns(TreePath path);
  }

  private static class Node implements Comparable<Node> {
    /** The logical order of this node in the flattened tree. */
    private final int myOrder;
    /** All the direct descendants of this node. */
    private final List<Node> myChildren;
    /** The column data for this node. */
    private final String[] myColumns;

    private static final int INDENT_SIZE = 4;
    private static final String INDENT = "│   ";
    private static final String INDENT_LAST = "    ";
    private static final String BRANCH = "├── ";
    private static final String BRANCH_LAST = "└── ";

    public Node(String[] columns, int order) {
      myColumns = columns;
      myOrder = order;
      myChildren = new ArrayList<>();
    }

    /**
     * Sorts all direct and indirect descendant nodes into logical order.
     */
    public void sort() {
      Collections.sort(myChildren);
      for (Node child : myChildren) {
        child.sort();
      }
    }

    /**
     * Populates {@param maxColumnWidths} with the maximum column width for each column
     * of this node and all descendants.
     * @param indent the indentation of this node in number of characters.
     */
    public void measure(@NotNull List<Integer> maxColumnWidths, int indent) {
      // Grow maxColumnWidths to at least as big as myColumns.
      while (maxColumnWidths.size() < myColumns.length) {
        maxColumnWidths.add(0);
      }
      for (int i = 0, c = myColumns.length; i < c; i++) {
        int width = myColumns[i].length();
        // Consider the tree as part of the first column.
        if (i == 0) { width += indent; }
        // Padding between columns.
        if (i < c - 1) { width += 1; }
        maxColumnWidths.set(i, max(width, maxColumnWidths.get(i)));
      }
      // Now measure all the children.
      for (Node child : myChildren) {
        child.measure(maxColumnWidths, indent + INDENT_SIZE);
      }
    }

    /**
     * Prints this node and all descendants to the {@link StringBuilder}.
     * This includes all tree lines, and padding for each column.
     *
     * @param maxColumnWidths the maximum column widths calculated by {@link #measure}.
     */
    public void print(StringBuffer sb, List<Integer> maxColumnWidths) {
      print(sb, maxColumnWidths, "", true, false);
    }

    public void print(StringBuffer sb, List<Integer> maxColumnWidths, String prefix, boolean root, boolean last) {
      StringBuffer column = new StringBuffer();
      if (!root) {
        column.append(prefix);
        column.append(last ? BRANCH_LAST : BRANCH);
        prefix += last ? INDENT_LAST : INDENT;
      }
      for (int i = 0, c = myColumns.length; i < c; i++) {
        column.append(myColumns[i]);
        if (i < c - 1) {
          // Align columns
          while (column.length() < maxColumnWidths.get(i)) {
            column.append(' ');
          }
        }
        sb.append(column.toString());
        column.setLength(0);
      }
      sb.append("\n");

      int childCount = myChildren.size();
      for (int i = 0; i < childCount; i++) {
        myChildren.get(i).print(sb, maxColumnWidths, prefix, false, i == childCount-1);
      }
    }

    @Override
    public int compareTo(Node o) {
      return myOrder - o.myOrder;
    }
  }
}
