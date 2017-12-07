// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.project.build.invoker;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TasksPerProject {
  @NotNull private final ListMultimap<Path, String> myTasksPerProjectMap = ArrayListMultimap.create();

  public void addTasks(@NotNull String projectPath, @NotNull List<String> tasks) {
    Path path = Paths.get(projectPath);
    myTasksPerProjectMap.putAll(path, tasks);
  }

  public boolean isEmpty() {
    return myTasksPerProjectMap.isEmpty();
  }

  public void add(@NotNull TasksPerProject tasks) {
    for (Map.Entry<Path, String> task : tasks.myTasksPerProjectMap.entries()) {
      if (!myTasksPerProjectMap.values().contains(task.getValue())) {
        myTasksPerProjectMap.put(task.getKey(), task.getValue());
      }
    }
  }

  @NotNull
  public Set<Path> getProjectPaths() {
    return myTasksPerProjectMap.keys().elementSet();
  }

  @NotNull
  public List<String> getTasks(@NotNull Path projectPath) {
    return myTasksPerProjectMap.get(projectPath);
  }
}
