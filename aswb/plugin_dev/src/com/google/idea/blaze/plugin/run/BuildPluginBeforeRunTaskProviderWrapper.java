/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.plugin.run;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a {@link BeforeRunTaskProvider}, creating an implementation of the same based on experiment
 * config.
 */
public class BuildPluginBeforeRunTaskProviderWrapper<T extends BeforeRunTask<?>>
    extends BeforeRunTaskProvider<T> {

  private final BeforeRunTaskProvider<T> wrapped;

  @SuppressWarnings("unchecked")
  public BuildPluginBeforeRunTaskProviderWrapper(Project project) {
    if (BlazeIntellijPluginConfigurationType.PORTABLE_DEPLOYER_ENABLED) {
      throw new IllegalStateException("Not implemented yet");
    } else {
      wrapped = (BeforeRunTaskProvider<T>) new BuildPluginBeforeRunTaskProvider(project);
    }
  }

  @Override
  public Key<T> getId() {
    return wrapped.getId();
  }

  @Override
  public String getName() {
    return wrapped.getName();
  }

  @Override
  public String getDescription(T task) {
    return wrapped.getDescription(task);
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull T task) {
    return wrapped.canExecuteTask(configuration, task);
  }

  @Override
  @Nullable
  public T createTask(@NotNull RunConfiguration runConfiguration) {
    return wrapped.createTask(runConfiguration);
  }

  @Override
  public boolean executeTask(
      @NotNull DataContext dataContext,
      @NotNull RunConfiguration runConfiguration,
      @NotNull ExecutionEnvironment executionEnvironment,
      @NotNull T t) {
    return wrapped.executeTask(dataContext, runConfiguration, executionEnvironment, t);
  }
}
