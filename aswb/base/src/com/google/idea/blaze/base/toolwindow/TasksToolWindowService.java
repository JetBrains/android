/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
import com.intellij.openapi.project.Project;

/** Service that controls the Blaze Outputs Tool Window. */
public interface TasksToolWindowService {

  static TasksToolWindowService getInstance(Project project) {
    return project.getService(TasksToolWindowService.class);
  }

  void startTask(Task task, ImmutableList<Filter> consoleFilters);

  void output(Task task, PrintOutput output);

  void status(Task task, StatusOutput output);

  void state(Task task, StateUpdate output);

  void finishTask(Task task, Task.Status status);

  void moveTask(Task task, Task newParent);

  void makeTaskRoot(Task task);

  void navigate(Task task, HyperlinkInfo link, int offset);

  void removeTask(Task task);

  void activate();

  void setStopHandler(Task task, Runnable runnable);

  void removeStopHandler(Task task);
}
