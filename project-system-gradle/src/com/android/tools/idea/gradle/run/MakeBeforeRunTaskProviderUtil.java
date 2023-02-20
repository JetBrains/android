/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import static java.util.stream.Collectors.toList;

import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.google.common.collect.Lists;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class MakeBeforeRunTaskProviderUtil {
  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(MakeBeforeRunTaskProviderUtil.class);
  }

  /**
   * Returns the list of {@link RunConfiguration} of the project that don't have an active {@link MakeBeforeRunTask}
   * in the list of {@link RunManagerEx#getBeforeRunTasks(Key)}.
   */
  public static List<RunConfiguration> getConfigurationsMissingBeforeRunTask(@NotNull Project project) {
    if (project.isDisposed()) {
      return new ArrayList<>();
    }

    return RunManagerEx.getInstanceEx(project).getAllConfigurationsList().stream()
      .filter(config -> isBeforeRunTaskMissing(project, config))
      .collect(Collectors.toList());
  }

  /**
   * Adds a {@link MakeBeforeRunTask} to all {@link RunConfiguration} of the project if it is missing.
   * Returns the list of {@link RunConfiguration} that have been fixed.
   */
  public static List<RunConfiguration> fixConfigurationsMissingBeforeRunTask(@NotNull Project project) {
    List<RunConfiguration> result = new ArrayList<>();
    if (project.isDisposed()) {
      return result;
    }

    getLogger().info(String.format("Trying to fix run configurations of project \"%s\"", project.getName()));
    List<RunConfiguration> list = getConfigurationsMissingBeforeRunTask(project);
    for (RunConfiguration config : list) {
      getLogger().info(String.format("Trying to fix config \"%s\"", config.getName()));
      if (addBeforeRunTaskToConfig(project, config)) {
        result.add(config);
      }
    }
    return result;
  }

  public static void ensureMakeBeforeRunTaskInConfigurationTemplate(@NotNull Project project) {
    RunManager runManager = RunManagerEx.getInstance(project);
    RunConfiguration configuration = runManager.getConfigurationTemplate(new AndroidRunConfigurationType().getFactory()).getConfiguration();
    if (configuration.getBeforeRunTasks().isEmpty()) {
      configuration.setBeforeRunTasks(Lists.newArrayList(new MakeBeforeRunTaskProvider().createTask(configuration)));
    }
  }

  private static boolean isBeforeRunTaskMissing(@NotNull Project project, @NotNull RunConfiguration config) {
    BeforeRunTaskProvider taskProvider = BeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID);
    if (!(taskProvider instanceof MakeBeforeRunTaskProvider)) {
      return false;
    }

    // Check the configuration should have the before run task by default
    MakeBeforeRunTaskProvider beforeRunTaskProvider = ((MakeBeforeRunTaskProvider)taskProvider);
    if (!beforeRunTaskProvider.configurationTypeIsSupported(config)) {
      return false;
    }
    if (!beforeRunTaskProvider.configurationTypeIsEnabledByDefault(config)) {
      return false;
    }

    // Check at least one before run task is enabled
    List<MakeBeforeRunTask> tasks = RunManagerEx.getInstanceEx(project).getBeforeRunTasks(config, MakeBeforeRunTaskProvider.ID);
    if (tasks.stream().anyMatch(task -> task.isEnabled())) {
      return false;
    }
    return true;
  }

  private static boolean addBeforeRunTaskToConfig(@NotNull Project project, @NotNull RunConfiguration config) {
    RunManagerEx runManagerEx = RunManagerEx.getInstanceEx(project);
    MakeBeforeRunTaskProvider provider =
      (MakeBeforeRunTaskProvider)BeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID);
    if (provider == null) {
      getLogger().warn(String.format("Skipping config \"%s\" because task provider is not found", config.getName()));
      return false;
    }
    MakeBeforeRunTask newTask = provider.createTask(config);
    if (newTask == null) {
      getLogger().warn(String.format("Skipping config \"%s\" because provider returned a null task", config.getName()));
      return false;
    }
    List<BeforeRunTask> tasks = runManagerEx.getBeforeRunTasks(config).stream().collect(toList());
    tasks.add(newTask);
    runManagerEx.setBeforeRunTasks(config, tasks);
    getLogger().info(String.format("Added missing task to config \"%s\"", config.getName()));
    return true;
  }
}
