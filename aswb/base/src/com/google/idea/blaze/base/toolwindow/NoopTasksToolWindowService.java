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

/**
 * A Fake {@link TasksToolWindowService} for integration tests that does nothing.
 *
 * <p>Since integration tests do not register any Tool Windows, TasksToolWindowService would run
 * into exceptions when trying to access Tool Window objects.
 */
public final class NoopTasksToolWindowService implements TasksToolWindowService {

  @Override
  public void startTask(Task task, ImmutableList<Filter> consoleFilters) {}

  @Override
  public void output(Task task, PrintOutput output) {}

  @Override
  public void status(Task task, StatusOutput output) {}

  @Override
  public void state(Task task, StateUpdate output) {}

  @Override
  public void finishTask(Task task, Task.Status status) {}

  @Override
  public void moveTask(Task task, Task newParent) {}

  @Override
  public void makeTaskRoot(Task task) {}

  @Override
  public void navigate(Task task, HyperlinkInfo link, int offset) {}

  @Override
  public void removeTask(Task task) {}

  @Override
  public void activate() {}

  @Override
  public void setStopHandler(Task task, Runnable runnable) {}

  @Override
  public void removeStopHandler(Task task) {}
}
