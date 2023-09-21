/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

final class ExecutionTargetService {
  @NotNull
  private final Project myProject;

  @NotNull
  private final Function<Project, ExecutionTargetManager> myExecutionTargetManagerGetInstance;

  @NotNull
  private final Function<Project, RunManager> myRunManagerGetInstance;

  @SuppressWarnings("unused")
  private ExecutionTargetService(@NotNull Project project) {
    this(project, ExecutionTargetManager::getInstance, RunManager::getInstance);
  }

  @VisibleForTesting
  @NonInjectable
  ExecutionTargetService(@NotNull Project project,
                         @NotNull Function<Project, ExecutionTargetManager> executionTargetManagerGetInstance,
                         @NotNull Function<Project, RunManager> runManagerGetInstance) {
    myProject = project;
    myExecutionTargetManagerGetInstance = executionTargetManagerGetInstance;
    myRunManagerGetInstance = runManagerGetInstance;
  }

  @NotNull
  static ExecutionTargetService getInstance(@NotNull Project project) {
    return project.getService(ExecutionTargetService.class);
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxExecutionTarget getActiveTarget() {
    return (DeviceAndSnapshotComboBoxExecutionTarget)myExecutionTargetManagerGetInstance.apply(myProject).getActiveTarget();
  }

  void setActiveTarget(@NotNull DeviceAndSnapshotComboBoxExecutionTarget target) {
    ExecutionTargetManager executionTargetManager = myExecutionTargetManagerGetInstance.apply(myProject);

    if (executionTargetManager.getActiveTarget().equals(target)) {
      return;
    }

    // In certain test scenarios, this action may get updated in the main test thread instead of the EDT thread (is this correct?).
    // So we'll just make sure the following gets run on the EDT thread and wait for its result.
    ApplicationManager.getApplication().invokeAndWait(() -> {
      RunManager runManager = myRunManagerGetInstance.apply(myProject);
      RunnerAndConfigurationSettings settings = runManager.getSelectedConfiguration();

      // There is a bug in {@link com.intellij.execution.impl.RunManagerImplKt#clear(boolean)} where it's possible the selected setting's
      // RunConfiguration is be non-existent in the RunManager. This happens when temporary/shared RunnerAndConfigurationSettings are
      // cleared from the list of RunnerAndConfigurationSettings, and the selected RunnerAndConfigurationSettings is temporary/shared and
      // left dangling.
      if (settings == null || runManager.findSettings(settings.getConfiguration()) == null) {
        return;
      }

      executionTargetManager.setActiveTarget(target);
    });
  }
}
