/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.toolwindow;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Tabs of the tool-window. All the tasks that are added to the tool-window are grouped by their
 * types into tabs. Each tab contains a tree and consoles combination.
 */
final class ToolWindowTabs {
  private final Project project;
  private final Map<Task.Type, Tab> tabs = new EnumMap<>(Task.Type.class);
  private ContentManager contentManager;

  ToolWindowTabs(Project project) {
    this.project = project;
  }

  void addTask(Task task, ImmutableList<Filter> consoleFilters, Disposable parentDisposable) {
    Tab tab = tabs.computeIfAbsent(task.getType(), this::newTab);
    tab.behaviour.addTask(task, project, consoleFilters, parentDisposable);
    // Only auto-select top level tasks
    if (task.getParent().isEmpty()) {
      getContentManager().setSelectedContent(tab.content);
    }
  }

  void removeTask(Task task) {
    Tab tab = getTab(task);
    tab.behaviour.removeTask(task);
  }

  void finishTask(Task task) {
    Tab tab = getTab(task);
    tab.behaviour.finishTask(task);
  }

  void taskOutput(Task task, PrintOutput output) {
    getTab(task).behaviour.taskOutput(task, output);
  }

  void statusOutput(Task task, StatusOutput output) {
    getTab(task).behaviour.taskStatus(task, output);
  }

  void updateState(Task task, StateUpdate output) {
    getTab(task).behaviour.taskState(task, output);
  }

  void navigate(Task task, HyperlinkInfo link, int offset) {
    Tab tab = getTab(task);
    tab.behaviour.navigate(task, link, offset);
    getContentManager().setSelectedContent(tab.content);
  }

  void setStopHandler(Task task, @Nullable Runnable runnable) {
    getTab(task).behaviour.setStopHandler(task, runnable);
  }

  private Tab getTab(Task task) {
    Tab tab = tabs.get(task.getType());
    if (tab == null) {
      throw new IllegalStateException(
          "Task `" + task.getName() + "` with type `" + task.getType() + "` doesn't have tab.");
    }
    return tab;
  }

  private Tab newTab(Task.Type type) {
    TasksTreeConsoleBehaviour behaviour = new TasksTreeConsoleBehaviour();
    TasksTreeConsoleModel model = TasksTreeConsoleModel.create(project, behaviour);
    Content content = createToolWindowContent(model, type);
    getContentManager().addContent(content);
    return new Tab(behaviour, content);
  }

  private Content createToolWindowContent(TasksTreeConsoleModel model, Task.Type type) {
    Disposable viewParentDisposable = Disposer.newDisposable();
    Content content =
        ContentFactory.SERVICE
            .getInstance()
            .createContent(
                new TasksTreeConsoleView(model, viewParentDisposable).getComponent(),
                type.getDisplayName(),
                false);
    content.setCloseable(false);
    content.setDisposer(viewParentDisposable);
    return content;
  }

  private ContentManager getContentManager() {
    if (contentManager != null) {
      return contentManager;
    }
    ToolWindow toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(TasksToolWindowFactory.ID);
    if (toolWindow == null) {
      throw new IllegalStateException("Toolwindow " + TasksToolWindowFactory.ID + " doesn't exist");
    }
    contentManager = toolWindow.getContentManager();
    return contentManager;
  }

  private static class Tab {
    final TasksTreeConsoleBehaviour behaviour;
    final Content content;

    Tab(TasksTreeConsoleBehaviour behaviour, Content content) {
      this.behaviour = behaviour;
      this.content = content;
    }
  }
}
