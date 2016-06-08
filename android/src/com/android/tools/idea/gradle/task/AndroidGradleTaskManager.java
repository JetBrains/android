/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.task;

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

/**
 * Executes Gradle tasks.
 */
public class AndroidGradleTaskManager implements GradleTaskManagerExtension {
  @Override
  public boolean executeTasks(@NotNull ExternalSystemTaskId id,
                              @NotNull List<String> taskNames,
                              @NotNull String projectPath,
                              @Nullable GradleExecutionSettings settings,
                              @NotNull final List<String> vmOptions,
                              @NotNull final List<String> scriptParameters,
                              @Nullable String debuggerSetup,
                              @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException
  {
    GradleInvoker invoker = getInvoker();
    if (invoker == null) {
      // Returning false gives control back to the framework, and the task(s) will be invoked by IDEA.
      return false;
    }
    invoker.executeTasks(taskNames, vmOptions, scriptParameters, id, listener, null, true, false);
    return true;
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) {
    GradleInvoker invoker = getInvoker();
    if (invoker == null) {
      return false;
    }
    invoker.cancelTask(id);
    return true;
  }

  @Nullable
  private static GradleInvoker getInvoker() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 1 && !openProjects[0].isDefault()) {
      Project project = openProjects[0];
      if (Projects.requiresAndroidModel(project) && Projects.isDirectGradleInvocationEnabled(project)) {
        return GradleInvoker.getInstance(project);
      }
    }
    return null;
  }
}
