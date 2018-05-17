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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.run.LaunchCompatibility.State;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetConfigurable;
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxTargetProvider extends DeployTargetProvider {
  private final @NotNull Supplier<DeviceAndSnapshotComboBoxAction> myDeviceAndSnapshotComboBoxActionGetInstance;
  private final @NotNull DialogSupplier mySelectedDevicesErrorDialog;

  // TODO This should not be used in tests
  public DeviceAndSnapshotComboBoxTargetProvider() {
    this(DeviceAndSnapshotComboBoxAction::getInstance, SelectedDevicesErrorDialog::new);
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxTargetProvider(@NotNull Supplier<DeviceAndSnapshotComboBoxAction> deviceAndSnapshotComboBoxActionGetInstance,
                                          @NotNull DialogSupplier selectedDevicesErrorDialog) {
    myDeviceAndSnapshotComboBoxActionGetInstance = deviceAndSnapshotComboBoxActionGetInstance;
    mySelectedDevicesErrorDialog = selectedDevicesErrorDialog;
  }

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Use the device/snapshot drop down";
  }

  @NotNull
  @Override
  public DeployTargetState createState() {
    return DeployTargetState.DEFAULT_STATE;
  }

  @NotNull
  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     @NotNull Disposable parent,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return DeployTargetConfigurable.DEFAULT_CONFIGURABLE;
  }

  @Override
  public boolean requiresRuntimePrompt(@NotNull Project project) {
    List<Device> devicesWithError = selectedDevicesWithError(project);
    if (devicesWithError.isEmpty()) {
      return false;
    }

    boolean anyDeviceHasError = devicesWithError.stream().anyMatch(d -> d.getLaunchCompatibility().getState() == State.ERROR);
    // Show dialog if any device has an error or if DO_NOT_SHOW_WARNING_ON_DEPLOYMENT is not true (null or false).
    return anyDeviceHasError || !Objects.equals(project.getUserData(SelectedDevicesErrorDialog.DO_NOT_SHOW_WARNING_ON_DEPLOYMENT), true);
  }

  @NotNull
  private List<Device> selectedDevicesWithError(@NotNull Project project) {
    List<Device> selectedDevices = myDeviceAndSnapshotComboBoxActionGetInstance.get().getSelectedDevices(project);
    return selectedDevices.stream().filter(d -> !d.getLaunchCompatibility().getState().equals(State.OK)).collect(Collectors.toList());
  }

  @Override
  public @Nullable DeployTarget showPrompt(@NotNull Project project) {
    List<Device> devicesWithError = selectedDevicesWithError(project);
    if (!devicesWithError.isEmpty()) {
      if (!mySelectedDevicesErrorDialog.get(project, devicesWithError).showAndGet()) {
        return null;
      }
    }

    return new DeviceAndSnapshotComboBoxTarget(myDeviceAndSnapshotComboBoxActionGetInstance.get()::getSelectedTargets);
  }

  @NotNull
  @Override
  public DeployTarget getDeployTarget(@NotNull Project project) {
    return new DeviceAndSnapshotComboBoxTarget(myDeviceAndSnapshotComboBoxActionGetInstance.get()::getSelectedTargets);
  }
}
