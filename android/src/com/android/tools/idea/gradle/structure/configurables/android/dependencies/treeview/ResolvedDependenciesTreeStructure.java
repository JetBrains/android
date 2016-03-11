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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY;

public class ResolvedDependenciesTreeStructure extends AbstractTreeStructure {
  @NotNull private final RootNode myRootNode;

  ResolvedDependenciesTreeStructure(@NotNull PsdAndroidModuleModel moduleModel) {
    myRootNode = new RootNode(moduleModel);
  }

  @Override
  public Object getRootElement() {
    return myRootNode;
  }

  boolean settingsChanged() {
    return myRootNode.settingsChanged();
  }

  @Override
  public Object[] getChildElements(Object element) {
    if (element instanceof SimpleNode) {
      SimpleNode[] children = ((SimpleNode)element).getChildren();
      if (children != null) {
        return children;
      }
    }
    return EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public Object getParentElement(Object element) {
    if (element instanceof NodeDescriptor) {
      return ((NodeDescriptor)element).getParentDescriptor();
    }
    return null;
  }

  @Override
  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element instanceof NodeDescriptor) {
      return (NodeDescriptor)element;
    }
    throw new IllegalArgumentException("Failed to find a node descriptor for " + element);
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }
}
