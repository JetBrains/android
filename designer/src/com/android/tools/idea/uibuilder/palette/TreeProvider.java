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
package com.android.tools.idea.uibuilder.palette;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TreeProvider extends AbstractTreeStructure {
  private final Project myProject;
  private final Palette myPalette;

  public TreeProvider(@NotNull Project project, @NotNull Palette palette) {
    myProject = project;
    myPalette = palette;
  }

  @NotNull
  @Override
  public Object getRootElement() {
    return myPalette;
  }

  @NotNull
  @Override
  public Object[] getChildElements(@NotNull Object element) {
    if (element instanceof Palette) {
      return ((Palette)element).getItems().toArray();
    }
    else if (element instanceof Palette.Group) {
      return ((Palette.Group)element).getItems().toArray();
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  @Override
  public Object getParentElement(@NotNull Object element) {
    if (element instanceof Palette.Item) {
      return ((Palette.Item)element).getParent();
    }
    else if (element instanceof Palette.Group) {
      return ((Palette.Group)element).getParent();
    }
    return null;
  }

  @NotNull
  @Override
  public NodeDescriptor createDescriptor(@NotNull Object element, @Nullable NodeDescriptor parentDescriptor) {
    return new NodeDescriptor(myProject, parentDescriptor) {
      @Override
      public boolean update() {
        return false;
      }

      @Override
      public Object getElement() {
        return element;
      }
    };
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }
}
