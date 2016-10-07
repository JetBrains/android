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

import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicBoolean;

public class Tree extends com.intellij.ui.treeStructure.Tree {
  @NotNull private AtomicBoolean myFireSelectionEvents = new AtomicBoolean(true);

  public Tree() {
  }

  public Tree(TreeNode root) {
    super(root);
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
  }

  @Override
  public void addTreeSelectionListener(final TreeSelectionListener listener) {
    super.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myFireSelectionEvents.get()) {
          listener.valueChanged(e);
        }
      }
    });
  }

  @Override
  public void setSelectionPath(TreePath path) {
    setSelectionPath(path, true);
  }

  public void setSelectionPath(TreePath path, boolean fireEvents) {
    boolean previousValue = myFireSelectionEvents.getAndSet(fireEvents);
    try {
      super.setSelectionPath(path);
    } finally {
      myFireSelectionEvents.set(previousValue);
    }
  }

  @Override
  public void setSelectionPaths(TreePath[] paths) {
    setSelectionPaths(paths, true);
  }

  public void setSelectionPaths(TreePath[] paths, boolean fireEvents) {
    boolean previousValue = myFireSelectionEvents.getAndSet(fireEvents);
    try {
      super.setSelectionPaths(paths);
    } finally {
      myFireSelectionEvents.set(previousValue);
    }
  }
}
