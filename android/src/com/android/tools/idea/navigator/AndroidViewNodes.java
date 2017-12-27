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

import com.android.annotations.VisibleForTesting;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidViewNodes {
  @Nullable
  public <T> T selectNodeOfType(@NotNull Class<T> nodeType, @NotNull Project project) {
    ToolWindow projectToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Project");
    if (projectToolWindow != null) {
      return selectNodeOfType(nodeType, project, projectToolWindow);
    }
    return null;
  }

  @VisibleForTesting
  <T> T selectNodeOfType(@NotNull Class<T> nodeType, @NotNull Project project, @NotNull ToolWindow projectToolWindow) {
    Ref<T> nodeRef = new Ref<>();
    // Activate (show) the  "Project" view, select the "Android" panel, and look for the node.
    projectToolWindow.activate(() -> {
      T found = performOnNodeOfType(nodeType, (node, treeBuilder) -> treeBuilder.select(node), project);
      nodeRef.set(found);
    });
    return nodeRef.get();
  }

  @Nullable
  public <T> T findAndRefreshNodeOfType(@NotNull Class<T> nodeType, @NotNull Project project) {
    return performOnNodeOfType(nodeType, (node, treeBuilder) -> treeBuilder.queueUpdateFrom(node, false), project);
  }

  @Nullable
  private static <T> T performOnNodeOfType(@NotNull Class<T> nodeType,
                                           @NotNull PerformOnNodeTask<T> task,
                                           @NotNull Project project) {
    ProjectView projectView = ProjectView.getInstance(project);
    AbstractProjectViewPane androidViewPane = projectView.getProjectViewPaneById(AndroidProjectViewPane.ID);
    assert androidViewPane != null;
    AbstractTreeBuilder treeBuilder = androidViewPane.getTreeBuilder();
    if (!treeBuilder.isDisposed()) {
      Ref<T> nodeRef = new Ref<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        treeBuilder.accept(nodeType, (TreeVisitor<T>)node -> {
          nodeRef.set(node);
          return true;
        });
      });
      T node = nodeRef.get();
      if (node != null) {
        task.perform(node, treeBuilder);
        return node;
      }
    }
    return null;
  }

  private interface PerformOnNodeTask<T> {
    void perform(@NotNull T node, @NotNull AbstractTreeBuilder treeBuilder);
  }
}
