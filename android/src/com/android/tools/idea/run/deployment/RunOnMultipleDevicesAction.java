/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class RunOnMultipleDevicesAction extends AnAction {
  @NotNull
  private final Function<Project, RunnerAndConfigurationSettings> myGetSelectedConfiguration;

  RunOnMultipleDevicesAction() {
    this(project -> RunManager.getInstance(project).getSelectedConfiguration());
  }

  @VisibleForTesting
  RunOnMultipleDevicesAction(@NotNull Function<Project, RunnerAndConfigurationSettings> getSelectedConfiguration) {
    super("Run on Multiple Devices", null, AllIcons.Actions.Execute);
    myGetSelectedConfiguration = getSelectedConfiguration;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Presentation presentation = event.getPresentation();

    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    RunnerAndConfigurationSettings settings = myGetSelectedConfiguration.apply(project);

    if (settings == null) {
      presentation.setEnabled(false);
      return;
    }

    Object configuration = settings.getConfiguration();

    if (!(configuration instanceof AndroidRunConfiguration)) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    DeviceAndSnapshotComboBoxTargetProvider provider = findProvider();

    if (provider == null) {
      return;
    }

    provider.setProvidingMultipleTargets(true);
    ExecutionEnvironmentBuilder builder = createBuilder(event.getDataContext());

    if (builder == null) {
      return;
    }

    ExecutionManager.getInstance(project).restartRunProfile(builder.build());
  }

  @Nullable
  private static DeviceAndSnapshotComboBoxTargetProvider findProvider() {
    Optional<DeployTargetProvider> optionalProvider = DeployTargetProvider.getProviders().stream()
      .filter(DeviceAndSnapshotComboBoxTargetProvider.class::isInstance)
      .findFirst();

    return (DeviceAndSnapshotComboBoxTargetProvider)optionalProvider.orElse(null);
  }

  @Nullable
  private static ExecutionEnvironmentBuilder createBuilder(@NotNull DataContext context) {
    Executor executor = ExecutorRegistry.getInstance().getExecutorById("Run");
    Project project = context.getData(CommonDataKeys.PROJECT);

    if (project == null) {
      return null;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();

    if (settings == null) {
      return null;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);

    if (builder == null) {
      return null;
    }

    builder.dataContext(context);
    return builder;
  }
}
