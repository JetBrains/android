/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.output.StateUpdate;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.TimeSource;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.serviceContainer.NonInjectable;
import java.time.Instant;

/** Service that controls the Blaze Outputs Tool Window. */
final class TasksToolWindowServiceImpl implements TasksToolWindowService, Disposable {

  private final TimeSource timeSource;
  private final ToolWindowTabs tabs;
  private final Project project;

  TasksToolWindowServiceImpl(Project project) {
    this(project, Instant::now);
  }

  @VisibleForTesting
  @NonInjectable
  TasksToolWindowServiceImpl(Project project, TimeSource timeSource) {
    this.project = project;
    this.timeSource = timeSource;
    tabs = new ToolWindowTabs(project);
  }

  // The below methods might be better replaced by an event-based approach. When we touch this part
  // in the future, we should consider to refactor it.

  /** Mark the given task as started and notify the view to reflect the started task. */
  @Override
  public void startTask(Task task, ImmutableList<Filter> consoleFilters) {

    task.setStartTime(timeSource.now());
    ApplicationManager.getApplication().invokeLater(() -> tabs.addTask(task, consoleFilters, this));
  }

  /** Append new output to a task view. */
  @Override
  public void output(Task task, PrintOutput output) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.taskOutput(task, output));
  }

  /** Append new status to a task view. */
  @Override
  public void status(Task task, StatusOutput output) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.statusOutput(task, output));
  }

  /** Update the state in a task view. */
  @Override
  public void state(Task task, StateUpdate output) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.updateState(task, output));
  }

  /** Update the state and the view when task finishes */
  @Override
  public void finishTask(Task task, Task.Status status) {

    task.setEndTime(timeSource.now());
    task.setStatus(status);
    ApplicationManager.getApplication().invokeLater(() -> tabs.finishTask(task));
  }

  /** Move task to a new parent task */
  @Override
  public void moveTask(Task task, Task newParent) {
    task.setParent(newParent);
  }

  /** Make task a root, removing it from the current parent if any. */
  @Override
  public void makeTaskRoot(Task task) {
    task.setParent(null);
  }

  /** Open given task's output hyperlink */
  @Override
  public void navigate(Task task, HyperlinkInfo link, int offset) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.navigate(task, link, offset));
  }

  /** Remove a {@link Task}, including all children of that task */
  @Override
  public void removeTask(Task task) {

    tabs.removeTask(task);
  }

  /** Activate the view */
  @Override
  public void activate() {

    ToolWindow toolWindow =
        ToolWindowManager.getInstance(project).getToolWindow(TasksToolWindowFactory.ID);
    if (toolWindow != null) {
      toolWindow.activate(/* runnable= */ null, /* autoFocusContents= */ false);
    }
  }

  /** Set the action to be executed when the given task is being manually stopped in the UI. */
  @Override
  public void setStopHandler(Task task, Runnable runnable) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.setStopHandler(task, runnable));
  }

  /** Remove option to stop the task manually in the UI. */
  @Override
  public void removeStopHandler(Task task) {

    ApplicationManager.getApplication().invokeLater(() -> tabs.setStopHandler(task, null));
  }

  @Override
  public void dispose() {}
}
