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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * The Multiple Devices item in the combo box. If this is selected the selected configuration is deployed on the last device set selected
 * with the {@link ModifyDeviceSetDialog Modify Device Set dialog.}
 */
// TODO Reduce the visibility of MultipleDevicesAction and ID
public final class MultipleDevicesAction extends AnAction {
  public static final String ID = "MultipleDevices";

  @NotNull
  private final Function<Project, RunnerAndConfigurationSettings> myGetSelectedConfiguration;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  private final @NotNull Supplier<@NotNull DeviceAndSnapshotComboBoxAction> myDeviceAndSnapshotComboBoxActionGetInstance;

  @VisibleForTesting
  MultipleDevicesAction() {
    this(project -> RunManager.getInstance(project).getSelectedConfiguration(),
         DevicesSelectedService::getInstance,
         DeviceAndSnapshotComboBoxAction::getInstance);
  }

  @VisibleForTesting
  @NonInjectable
  MultipleDevicesAction(@NotNull Function<Project, RunnerAndConfigurationSettings> getSelectedConfiguration,
                        @NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance,
                        @NotNull Supplier<@NotNull DeviceAndSnapshotComboBoxAction> deviceAndSnapshotComboBoxActionGetInstance) {
    myGetSelectedConfiguration = getSelectedConfiguration;
    myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;
    myDeviceAndSnapshotComboBoxActionGetInstance = deviceAndSnapshotComboBoxActionGetInstance;
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

    if (!isSupportedRunConfigurationType(settings.getType())) {
      presentation.setEnabled(false);
      return;
    }

    if (myDevicesSelectedServiceGetInstance.apply(project).isDialogSelectionEmpty()) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myDeviceAndSnapshotComboBoxActionGetInstance.get().setMultipleDevicesSelected(Objects.requireNonNull(event.getProject()), true);
  }

  private static boolean isSupportedRunConfigurationType(@NotNull ConfigurationType type) {
    if (type instanceof AndroidRunConfigurationType) {
      return true;
    }
    if (StudioFlags.MULTIDEVICE_INSTRUMENTATION_TESTS.get() && type instanceof AndroidTestRunConfigurationType) {
      return true;
    }
    return false;
  }
}
