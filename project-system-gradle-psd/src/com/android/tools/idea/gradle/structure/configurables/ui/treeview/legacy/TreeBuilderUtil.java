// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.gradle.structure.configurables.ui.treeview.legacy;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true)
final class TreeBuilderUtil {
  static boolean isNodeOrChildSelected(@NotNull JTree tree, @NotNull DefaultMutableTreeNode node){
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return false;

    TreePath path = new TreePath(node.getPath());
    for (TreePath selectionPath : selectionPaths) {
      if (path.isDescendant(selectionPath)) return true;
    }

    return false;
  }
}
