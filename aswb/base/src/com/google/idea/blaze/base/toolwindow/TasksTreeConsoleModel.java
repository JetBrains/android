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

import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Model for the combination of the tree and output consoles. */
final class TasksTreeConsoleModel {
  private final TasksTreeModel treeModel;
  private final Map<Task, ConsoleView> consolesOfTasks = new HashMap<>();

  private final Set<Task> topLevelFinishedTasks = new LinkedHashSet<>();

  private TasksTreeConsoleModel(Project project) {
    treeModel = new TasksTreeModel(project);
  }

  static TasksTreeConsoleModel create(Project project, TasksTreeConsoleBehaviour behaviour) {
    TasksTreeConsoleModel model = new TasksTreeConsoleModel(project);
    behaviour.defineBehavior(model);
    return model;
  }

  TasksTreeModel getTreeModel() {
    return treeModel;
  }

  Map<Task, ConsoleView> getConsolesOfTasks() {
    return consolesOfTasks;
  }

  public Set<Task> getTopLevelFinishedTasks() {
    return topLevelFinishedTasks;
  }

  /** Removes and returns the oldest top level finished task. */
  @Nullable
  public Task pollOldestFinishedTask() {
    if (topLevelFinishedTasks.isEmpty()) {
      return null;
    }
    Task toRemove = topLevelFinishedTasks.iterator().next();
    topLevelFinishedTasks.remove(toRemove);
    return toRemove;
  }
}
