/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory tree representation of a file tree. Must be created with a file tree,
 * and then additional files (which may or may not exist) may be added to the representation.
 * Can be rendered to a {@link JTree} using the {@link FileTreeCellRenderer}.
 */
public class FileTreeModel implements TreeModel {

  /**
   * Root file that this model was created with.
   */
  @NotNull
  private final File myRoot;

  /**
   * Root of the data structure representation.
   */
  @NotNull
  private Node myRootNode;

  private boolean myHideIrrelevantFiles;

  public FileTreeModel(@NotNull File root, boolean hideIrrelevantFiles) {
    this(root);
    myHideIrrelevantFiles = hideIrrelevantFiles;
  }

  public FileTreeModel(@NotNull File root) {
    myRoot = root;
    myRootNode = makeTree(root);
  }

  /**
   * Return the root {@link Node} of this representation.
   */
  @Override
  public Object getRoot() {
    if (myHideIrrelevantFiles && !myRootNode.isProposedFile) {
      return null;
    }
    return myRootNode;
  }

  /**
   * Get the Nth child {@link Node} of the given parent.
   */
  @Override
  public Object getChild(Object parent, int index) {
    Node n = (Node)parent;
    if (!myHideIrrelevantFiles) {
      return n.children.get(index);
    }

    for (int i = 0; i < n.children.size(); i++) {
      Node child =  n.children.get(i);
      if (child.isProposedFile && index == 0) {
        return child;
      } else if (child.isProposedFile) {
        index--;
      }
    }

    return null;
  }

  /**
   * Get the number of children that the given parent {@link Node} has.
   */
  @Override
  public int getChildCount(Object parent) {
    if (!myHideIrrelevantFiles) {
      return ((Node)parent).children.size();
    }
    int count = 0;
    for (Node n : ((Node)parent).children) {
      if (n.isProposedFile) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns true iff the given {@link Node} has no children (is a leaf)
   */
  @Override
  public boolean isLeaf(Object node) {
    if (!myHideIrrelevantFiles) {
      return ((Node)node).children.isEmpty();
    }
    for (Node n : ((Node)node).children) {
      if (n.isProposedFile) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // Not implemented
  }

  /**
   * Returns the index of the given child inside the given parent or -1 if given node is not a child of the parent.
   */
  @Override
  public int getIndexOfChild(Object parent, Object child) {
    if (!myHideIrrelevantFiles) {
      //noinspection SuspiciousMethodCalls
      return ((Node)parent).children.indexOf(child);
    }
    Node n = (Node)parent;
    int index = 0;
    for (int i = 0; i < n.children.size(); i++) {
      Node candidate = n.children.get(i);
      if (candidate.equals(child)) {
        return index;
      }
      if (candidate.isProposedFile) {
        index++;
      }
    }
    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    // Not implemented
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    // Not implemented
  }

  /**
   * Check to see if there are any conflicts (multiple files added to the same location) in the tree.
   */
  @SuppressWarnings("unused")
  public boolean hasConflicts() {
    return treeHasConflicts(myRootNode);
  }

  /**
   * DFS through the tree looking for conflicted nodes.
   */
  private static boolean treeHasConflicts(@NotNull Node root) {
    if (root.isConflicted) {
      return true;
    }

    for (Node n : root.children) {
      if (treeHasConflicts(n)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Add the given file to the representation.
   * This is a no-op if the given path already exists within the tree.
   */
  @Nullable
  public Node addFile(@NotNull File f) {
    return addFile(f, null);
  }

  /**
   * Add the given file to the representation and mark it with the given icon.
   * This is a no-op if the given path already exists within the tree.
   */
  @Nullable
  public Node addFile(@NotNull File f, @Nullable Icon ic) {
    if (FileUtil.filesEqual(f, myRoot)) return null;
    String s = f.isAbsolute() ? FileUtil.getRelativePath(myRoot, f) : f.getPath();
    if (s != null) {
      List<String> parts = Lists.newLinkedList(Splitter.on(File.separatorChar).split(s));
      return makeNode(myRootNode, parts, ic, false);
    }
    return null;
  }

  /**
   * Add the given file to the representation and mark it with the given icon.
   * If the path already exists within the tree it will be marked as a conflicting path.
   */
  @Nullable
  public Node forceAddFile(@NotNull File f, @Nullable Icon ic) {
    String s = f.isAbsolute() ? FileUtil.getRelativePath(myRoot, f) : f.getPath();
    if (s != null) {
      List<String> parts = Lists.newLinkedList(Splitter.on(File.separatorChar).split(s));
      return makeNode(myRootNode, parts, ic, true);
    }
    return null;
  }

  public void sort(@NotNull Comparator<File> comparator) {
    sort(myRoot, myRootNode, comparator);
  }

  private static void sort(@NotNull File rootFile, @NotNull Node rootNode, @NotNull Comparator<File> comparator) {
    rootNode.children = rootNode.children
      .stream()
      .sorted((o1, o2) -> comparator.compare(new File(rootFile, o1.name), new File(rootFile, o2.name)))
      .collect(Collectors.toList());
    rootNode.children.forEach(childNode -> sort(new File(rootFile, childNode.name), childNode, comparator));
  }

  /**
   * Representation of a node within the tree
   */
  public static class Node {
    public String name;
    public List<Node> children = Lists.newLinkedList();
    public boolean existsOnDisk;
    public boolean isConflicted;
    public boolean isProposedFile;
    public Icon icon;

    @Override
    public String toString() {
      return name;
    }

    /**
     * Returns true iff this node has a child with the given name.
     */
    public boolean hasChild(String name) {
      for (Node child : children) {
        if (child.name.equals(name)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns the child with the given name or null
     */
    @Nullable
    public Node getChild(String name) {
      for (Node child : children) {
        if (child.name.equals(name)) {
          return child;
        }
      }
      return null;
    }
  }

  /**
   * Recursively build the node(s) specified in the given path hierarchy starting at the given root.
   * Mark the last node in the path with the given icon. If markConflict is set, mark the final node
   * as conflicted if it already exists.
   */
  @NotNull
  private static Node makeNode(@NotNull Node root, @NotNull List<String> path, @Nullable Icon ic, boolean markConflict) {
    root.isProposedFile = true;
    if (path.isEmpty()) {
      return root;
    }

    String name = path.get(0);

    if (markConflict) {
      if (path.size() == 1 && root.name.equals(name)) {
        root.isConflicted = true;
        return root;
      }
    }
    if (root.name.equals(name)) {
      // Continue down along already-created paths
      return makeNode(root, rest(path), ic, markConflict);
    } else if (root.hasChild(name)) {
      // Allow paths relative to root (rather than including root explicitly)
      if (markConflict && path.size() == 1) {
        Node targetNode = root.getChild(name);
        assert targetNode != null;
        targetNode.isConflicted = true;
        targetNode.icon = ic;
        targetNode.isProposedFile = true;
        return targetNode;
      }
      //noinspection ConstantConditions
      return makeNode(root.getChild(name), rest(path), ic, markConflict);
    } else {
      // If this node in the path doesn't exist, then create it.
      Node newNode = new Node();
      newNode.name = name;
      root.children.add(newNode);
      if (path.size() == 1) {
        // If this is the end of the path, mark with the given icon
        newNode.icon = ic;
        newNode.isProposedFile = true;
      } else {
        // Continue down to create the rest of the path
        return makeNode(newNode, rest(path), ic, markConflict);
      }
      return newNode;
    }
  }

  /**
   * Populate a tree from the file hierarchy rooted at the given file.
   */
  @NotNull
  private static Node makeTree(@NotNull File root) {
    Node n = new Node();
    n.name = root.getName();
    n.existsOnDisk = root.exists();
    if (root.isDirectory()) {
      File[] children = root.listFiles();
      if (children != null) {
        for (File f : children) {
          if (!f.isHidden()) {
            n.children.add(makeTree(f));
          }
        }
      }
    }
    return n;
  }

  /**
   * Convenience function. Operates on a list and returns a list containing all elements but the first.
   */
  private static <T> List<T> rest(List<T> list) {
    return list.subList(1, list.size());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb, myRootNode);
    return sb.toString();
  }

  /**
   * DFS over the tree to build a string representation e.g. (root (child (grandchild) (grandchild)) (child))
   */
  private void toString(StringBuilder sb, Node root) {
    sb.append('(');
    sb.append(root.name);
    if (!isLeaf(root)) {
      sb.append(' ');
    }
    for (Node child : root.children) {
      toString(sb, child);
    }
    sb.append(')');
  }
}
