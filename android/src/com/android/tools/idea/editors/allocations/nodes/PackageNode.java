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
package com.android.tools.idea.editors.allocations.nodes;

import com.android.ddmlib.AllocationInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.HashMap;
import java.util.Map;

public class PackageNode extends AbstractTreeNode {
  @NotNull
  private final String myName;

  @NotNull
  private final Map<String, PackageNode> myChildrenMap;

  public PackageNode(@NotNull String name) {
    myName = name;
    myChildrenMap = new HashMap<String, PackageNode>();
  }

  public void insert(String[] packages, AllocationInfo alloc, int depth) {
    if (depth < packages.length) {
      String name = packages[depth];
      PackageNode node = myChildrenMap.get(name);
      if (node == null) {
        node = depth == packages.length - 1 ? new ClassNode(name) : new PackageNode(name);
        myChildrenMap.put(name, node);
        addChild(node);
      }
      node.insert(packages, alloc, depth + 1);
    } else {
      addChild(new AllocNode(alloc));
    }
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public String getQualifiedName() {
    TreeNode parent = getParent();
    String pkg = parent instanceof PackageNode ? ((PackageNode)parent).getQualifiedName() : "";
    return pkg.isEmpty() ? myName : pkg + "." + myName;
  }
}
