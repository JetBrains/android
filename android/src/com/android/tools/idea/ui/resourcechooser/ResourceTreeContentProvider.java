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
package com.android.tools.idea.ui.resourcechooser;

import com.android.tools.idea.ui.resourcechooser.groups.ResourceChooserGroup;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Tree model for the resource list in the resource chooser
 */
class ResourceTreeContentProvider extends AbstractTreeStructure {
  private final NodeWrapper[] myNodes;
  private final ResourceChooserItem[][] myItems;

  public ResourceTreeContentProvider(ResourceChooserGroup[] groups) {
    myNodes = Arrays.stream(groups)
      .map(group -> new NodeWrapper(group))
      .toArray(NodeWrapper[]::new);
    myItems = new ResourceChooserItem[groups.length][];
  }

  @NotNull
  @Override
  public Object getRootElement() {
    return myNodes;
  }

  @NotNull
  @Override
  public Object[] getChildElements(@NotNull Object element) {
    if (element == myNodes) {
      return myNodes;
    }
    if (element instanceof NodeWrapper) {
      ResourceChooserGroup group = ((NodeWrapper)element).group;
      int index = ArrayUtil.indexOf(myNodes, group);
      if (index == -1) {
        return group.getItems().toArray();
      }
      ResourceChooserItem[] items = myItems[index];
      if (items == null) {
        items = group.getItems().toArray(new ResourceChooserItem[0]);
        myItems[index] = items;
      }

      return items;
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public Object getParentElement(@NotNull Object element) {
    // Not required for the tree operations used in this tree.
    // And by not storing parent pointers, we can reuse tree items
    // (in particular the attribute items) in all the panels
    return null;
  }

  @NotNull
  @Override
  public NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public void commit() {
  }

  /**
   * Class to hide the ugliness of dealing with Object[]
   */
  private static class NodeWrapper {
    private final ResourceChooserGroup group;

    private NodeWrapper(@NotNull ResourceChooserGroup group) {
      this.group = group;
    }

    @Override
    public String toString() {
      return group.getGroupLabel();
    }
  }
}
