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

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

final class NlComponentTreeModel implements TreeModel {
  private final NlComponent myRoot;

  NlComponentTreeModel() {
    this(null);
  }

  NlComponentTreeModel(NlComponent root) {
    myRoot = root;
  }

  @Override
  public Object getRoot() {
    return myRoot;
  }

  @Override
  public Object getChild(Object parent, int i) {
    return ((NlComponent)parent).getChild(i);
  }

  @Override
  public int getChildCount(Object parent) {
    return ((NlComponent)parent).getChildCount();
  }

  @Override
  public boolean isLeaf(Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    for (int i = 0, count = getChildCount(parent); i < count; i++) {
      if (getChild(parent, i).equals(child)) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public void addTreeModelListener(TreeModelListener listener) {
  }

  @Override
  public void removeTreeModelListener(TreeModelListener listener) {
  }
}
