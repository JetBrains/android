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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

final class DeviceAndSnapshotComboBoxTarget implements DeployTarget {
  private final @NotNull SelectedTargetSupplier myGetSelectedTargets;
  private final @NotNull Supplier<DeviceAndSnapshotComboBoxAction> myDeviceAndSnapshotComboBoxActionGetInstance;

  DeviceAndSnapshotComboBoxTarget(@NotNull SelectedTargetSupplier getSelectedTargets) {
    this(getSelectedTargets, DeviceAndSnapshotComboBoxAction::getInstance);
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxTarget(@NotNull SelectedTargetSupplier getSelectedTargets,
                                  @NotNull Supplier<DeviceAndSnapshotComboBoxAction> deviceAndSnapshotComboBoxActionGetInstance) {
    myGetSelectedTargets = getSelectedTargets;
    myDeviceAndSnapshotComboBoxActionGetInstance = deviceAndSnapshotComboBoxActionGetInstance;
  }

  @Override
  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return false;
  }

  @NotNull
  @Override
  public RunProfileState getRunProfileState(@NotNull Executor executor,
                                            @NotNull ExecutionEnvironment environment,
                                            @NotNull DeployTargetState state) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public DeviceFutures getDevices(@NotNull Project project) {
    List<Device> devices = myDeviceAndSnapshotComboBoxActionGetInstance.get().getDevices(project).orElse(Collections.emptyList());
    Set<Target> selectedTargets = myGetSelectedTargets.get(project, devices);
    Collection<Device> selectedDevices = Target.filterDevices(selectedTargets, devices);

    bootAvailableDevices(selectedTargets, selectedDevices, project);
    return newDeviceFutures(selectedDevices);
  }

  private static void bootAvailableDevices(@NotNull Collection<Target> selectedTargets,
                                           @NotNull Collection<Device> selectedDevices,
                                           @NotNull Project project) {
    Map<Key, Target> map = selectedTargets.stream().collect(Collectors.toMap(Target::getDeviceKey, target -> target));

    selectedDevices.stream()
      .filter(device -> !device.isConnected())
      .map(VirtualDevice.class::cast)
      .forEach(virtualDevice -> map.get(virtualDevice.getKey()).boot(virtualDevice, project));
  }

  private static @NotNull DeviceFutures newDeviceFutures(@NotNull Collection<Device> selectedDevices) {
    List<AndroidDevice> devices = selectedDevices.stream()
      .map(Device::getAndroidDevice)
      .collect(Collectors.toList());

    return new DeviceFutures(devices);
  }
}
