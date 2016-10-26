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

import com.google.common.base.Objects;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class TreeUtil {
  @Nullable/*if this path can not be found in this tree*/
  public static TreePath getTreePathInTree(TreePath treePath, JTree tree) {
    Object root = tree.getModel().getRoot();
    Object[] path = treePath.getPath();
    List<Object> newPath = new ArrayList<Object>();
    Object found = null;
    for (Object node : path) {
      if (found == null) {
        if (Objects.equal(root, node)) {
          found = root;
        }
        else {
          return null;
        }
      }
      else {
        Object foundChild = null;
        for (int i = 0; i < tree.getModel().getChildCount(found); i++) {
          Object child = tree.getModel().getChild(found, i);
          if (Objects.equal(node, child)) {
            foundChild = child;
            break;
          }
        }
        if (foundChild == null) {
          return null;
        }
        found = foundChild;
      }
      newPath.add(found);
    }
    return new TreePath(newPath.toArray());
  }
}
