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

import com.google.idea.common.ui.properties.Property;
import com.intellij.openapi.project.Project;

/** Model for tree of tasks. */
final class TasksTreeModel {
  private final TasksTreeProperty tasksTreeProperty;

  public TasksTreeModel(Project project) {
    tasksTreeProperty = new TasksTreeProperty(project);
  }

  private final Property<Task> selectedTask = new Property<>();

  TasksTreeProperty tasksTreeProperty() {
    return tasksTreeProperty;
  }

  Property<Task> selectedTaskProperty() {
    return selectedTask;
  }
}
