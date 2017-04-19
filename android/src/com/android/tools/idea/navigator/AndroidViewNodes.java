/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

public class AndroidViewNodes {
  public void findAndRefreshNode(@NotNull Project project, @NotNull Condition<DefaultMutableTreeNode> condition) {
    AbstractProjectViewPane androidViewPane = ProjectView.getInstance(project).getProjectViewPaneById(AndroidProjectViewPane.ID);
    assert androidViewPane != null;
    AbstractTreeBuilder treeBuilder = androidViewPane.getTreeBuilder();
    if (!treeBuilder.isDisposed()) {
      AbstractTreeUi ui = treeBuilder.getUi();
      DefaultMutableTreeNode found = findNode(ui.getRootNode(), ui.getTreeModel(), condition);
      if (found != null) {
        treeBuilder.queueUpdateFrom(found.getUserObject(), false /* do not force resort */);
      }
    }
  }

  @Nullable
  private static DefaultMutableTreeNode findNode(@NotNull DefaultMutableTreeNode start,
                                                 @NotNull TreeModel treeModel,
                                                 @NotNull Condition<DefaultMutableTreeNode> condition) {
    if (condition.value(start)) {
      return start;
    }
    for (int i = 0; i < treeModel.getChildCount(start); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)treeModel.getChild(start, i);
      DefaultMutableTreeNode found = findNode(child, treeModel, condition);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
}
