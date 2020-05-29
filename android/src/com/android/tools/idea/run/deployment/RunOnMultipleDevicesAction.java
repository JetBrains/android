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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collections;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NotNull;

final class RunOnMultipleDevicesAction extends AnAction {
  static final String ID = "RunOnMultipleDevices";

  private final @NotNull Function<@NotNull Project, @NotNull RunManager> myRunManagerGetInstance;
  private final @NotNull Function<@NotNull Project, @NotNull AsyncDevicesGetter> myAsyncDevicesGetterGetInstance;
  private final @NotNull BooleanSupplier myRunningInstrumentedTestsOnMultipleDevicesEnabledGet;

  @SuppressWarnings("unused")
  private RunOnMultipleDevicesAction() {
    this(RunManager::getInstance, AsyncDevicesGetter::getInstance, StudioFlags.MULTIDEVICE_INSTRUMENTATION_TESTS::get);
  }

  @VisibleForTesting
  @NonInjectable
  RunOnMultipleDevicesAction(@NotNull Function<@NotNull Project, @NotNull RunManager> runManagerGetInstance,
                             @NotNull Function<@NotNull Project, @NotNull AsyncDevicesGetter> asyncDevicesGetterGetInstance,
                             @NotNull BooleanSupplier runningInstrumentedTestsOnMultipleDevicesEnabledGet) {
    myRunManagerGetInstance = runManagerGetInstance;
    myAsyncDevicesGetterGetInstance = asyncDevicesGetterGetInstance;
    myRunningInstrumentedTestsOnMultipleDevicesEnabledGet = runningInstrumentedTestsOnMultipleDevicesEnabledGet;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Presentation presentation = event.getPresentation();

    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setVisible(true);

    RunnerAndConfigurationSettings configurationAndSettings = myRunManagerGetInstance.apply(project).getSelectedConfiguration();

    if (configurationAndSettings == null) {
      presentation.setEnabled(false);
      return;
    }

    if (!supports(configurationAndSettings)) {
      presentation.setEnabled(false);
      return;
    }

    if (myAsyncDevicesGetterGetInstance.apply(project).get().orElse(Collections.emptyList()).isEmpty()) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  private boolean supports(@NotNull RunnerAndConfigurationSettings configurationAndSettings) {
    switch (configurationAndSettings.getType().getId()) {
      case AndroidRunConfigurationType.ID:
        return true;
      case AndroidBuildCommonUtils.ANDROID_TEST_RUN_CONFIGURATION_TYPE:
        return myRunningInstrumentedTestsOnMultipleDevicesEnabledGet.getAsBoolean();
      default:
        return false;
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    RunnerAndConfigurationSettings configurationAndSettings = myRunManagerGetInstance.apply(project).getSelectedConfiguration();

    if (configurationAndSettings == null) {
      return;
    }

    try {
      project.putUserData(DeviceAndSnapshotComboBoxTargetProvider.MULTIPLE_DEPLOY_TARGETS, true);
      ProgramRunnerUtil.executeConfiguration(configurationAndSettings, DefaultRunExecutor.getRunExecutorInstance());
    }
    finally {
      project.putUserData(DeviceAndSnapshotComboBoxTargetProvider.MULTIPLE_DEPLOY_TARGETS, false);
    }
  }
}
