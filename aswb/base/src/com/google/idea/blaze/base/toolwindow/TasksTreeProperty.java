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

import com.google.common.base.Preconditions;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Property that represents the tree structure of {@link Task} instances. Top level tasks (tasks
 * without a parent) are considered to be children of an artificial root. The property provides the
 * ability to add the listeners for addition and removal of the tasks into the tree.
 */
final class TasksTreeProperty {
  private final Task root;
  // The weak hash map implementation is to indicate that orphaned nodes can be cleaned up
  // implicitly or explicitly. The explicit clean up is performed by the method
  // `cleanUpDetachedSubtree` below.
  private final Map<Task, List<Task>> adjacencyList = new WeakHashMap<>();

  private final Set<InvalidationListener> invalidationListeners = new HashSet<>();

  public TasksTreeProperty(Project project) {
    root = new Task(project, "root", Task.Type.OTHER);
  }

  Task getRoot() {
    return root;
  }

  List<Task> getChildren(Task task) {
    return adjacencyList.get(task);
  }

  Task getParent(Task task) {
    return task.getParent().orElse(root);
  }

  boolean isTopLevelTask(Task task) {
    return !root.equals(task) && task.getParent().isEmpty();
  }

  void addTask(Task task) {
    Preconditions.checkNotNull(task);
    List<Task> siblings = adjacencyList.computeIfAbsent(getParent(task), t -> new ArrayList<>());
    siblings.add(task);
    notifyTreeInvalidated(getParent(task));
  }

  void removeTask(Task task) {
    Preconditions.checkNotNull(task);
    Task parent = getParent(task);
    List<Task> children = adjacencyList.get(parent);

    if (children == null) {
      throw new IllegalStateException(
          "The tree doesn't have parent: " + parent + " for the task: " + task);
    }
    int taskIndex = children.indexOf(task);
    if (taskIndex < 0) {
      throw new IllegalStateException(
          "The tree doesn't have child: " + task + " for the parent: " + parent);
    }
    children.remove(task);
    cleanUpDetachedSubtree(task);
    notifyTreeInvalidated(parent);
  }

  /** Update the UI following a change in state of a task. */
  void updateTask(Task task) {
    notifyTreeInvalidated(task);
  }

  private void cleanUpDetachedSubtree(Task task) {
    List<Task> children = adjacencyList.remove(task);
    if (children != null) {
      for (Task child : children) {
        cleanUpDetachedSubtree(child);
      }
    }
  }

  private void notifyTreeInvalidated(Task task) {
    for (InvalidationListener listener : invalidationListeners) {
      listener.taskInvalidated(task);
    }
  }

  void addInvalidationListener(InvalidationListener listener) {
    invalidationListeners.add(listener);
  }

  void removeInvalidationListener(InvalidationListener listener) {
    invalidationListeners.remove(listener);
  }

  interface InvalidationListener {
    void taskInvalidated(Task task);
  }
}
