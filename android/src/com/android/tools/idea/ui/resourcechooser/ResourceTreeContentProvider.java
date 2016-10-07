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

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Tree model for the resource list in the resource chooser
 */
class ResourceTreeContentProvider extends AbstractTreeStructure {
  private final Object myTreeRoot = new Object();
  private final ResourceChooserGroup[] myGroups;
  private final Object[][] myItems;

  public ResourceTreeContentProvider(ResourceChooserGroup[] groups) {
    myGroups = groups;
    myItems = new Object[groups.length][];
  }

  @Override
  public Object getRootElement() {
    return myTreeRoot;
  }

  @Override
  public Object[] getChildElements(Object element) {
    if (element == myTreeRoot) {
      return myGroups;
    }
    if (element instanceof ResourceChooserGroup) {
      ResourceChooserGroup group = (ResourceChooserGroup)element;
      int index = ArrayUtil.indexOf(myGroups, group);
      if (index == -1) {
        return group.getItems().toArray();
      }
      Object[] items = myItems[index];
      if (items == null) {
        items = group.getItems().toArray();
        myItems[index] = items;
      }

      return items;
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public Object getParentElement(Object element) {
    // Not required for the tree operations used in this tree.
    // And by not storing parent pointers, we can reuse tree items
    // (in particular the attribute items) in all the panels
    return null;
  }

  @NotNull
  @Override
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public void commit() {
  }
}
