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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;

final class NlComponentTreeModel implements TreeModel {
  private final NlComponent myRoot;

  NlComponentTreeModel() {
    myRoot = null;
  }

  NlComponentTreeModel(@NotNull NlModel model) {
    List<NlComponent> components = model.getComponents();
    myRoot = components.isEmpty() ? null : components.get(0);
  }

  @Nullable
  @Override
  public Object getRoot() {
    return myRoot;
  }

  @Nullable
  @Override
  public Object getChild(@NotNull Object parent, int i) {
    return ((NlComponent)parent).getChild(i);
  }

  @Override
  public int getChildCount(@NotNull Object parent) {
    return ((NlComponent)parent).getChildCount();
  }

  @Override
  public boolean isLeaf(@NotNull Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(@Nullable TreePath path, @Nullable Object newValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getIndexOfChild(@NotNull Object parent, @NotNull Object child) {
    // noinspection SuspiciousMethodCalls
    return ((NlComponent)parent).getChildren().indexOf(child);
  }

  @Override
  public void addTreeModelListener(@Nullable TreeModelListener listener) {
  }

  @Override
  public void removeTreeModelListener(@Nullable TreeModelListener listener) {
  }
}
