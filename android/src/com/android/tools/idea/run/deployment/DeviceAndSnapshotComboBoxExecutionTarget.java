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

import com.android.ddmlib.IDevice;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import icons.StudioIcons;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * The combo box generates these {@link ExecutionTarget ExecutionTargets.} ExecutionTargets determine the state of the run, debug, and stop
 * (but <em>not</em> the apply changes) toolbar buttons.
 */
final class DeviceAndSnapshotComboBoxExecutionTarget extends AndroidExecutionTarget {
  @NotNull
  private final List<Device> myDevices;

  /**
   * ExecutionTarget equals comparisons use this ID. Two lists with the same devices but different orders should resolve to the same ID. Do
   * not depend on its exact format.
   */
  @NotNull
  private final String myId;

  DeviceAndSnapshotComboBoxExecutionTarget() {
    this(Collections.emptyList());
  }

  DeviceAndSnapshotComboBoxExecutionTarget(@NotNull Device device) {
    this(Collections.singletonList(device));
  }

  DeviceAndSnapshotComboBoxExecutionTarget(@NotNull List<Device> devices) {
    myDevices = devices;

    myId = myDevices.stream()
      .map(Device::getKey)
      .map(Key::toString)
      .sorted()
      .collect(Collectors.joining(", ", "device_and_snapshot_combo_box_target[", "]"));
  }

  @Override
  public boolean isApplicationRunning(@NotNull String appPackage) {
    return myDevices.stream().anyMatch(device -> device.isRunning(appPackage));
  }

  @NotNull
  @Override
  public Collection<IDevice> getDevices() {
    return myDevices.stream()
      .map(Device::getDdmlibDevice)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    switch (myDevices.size()) {
      case 0:
        return "No Devices";
      case 1:
        return myDevices.get(0).getName();
      default:
        return "Multiple Devices";
    }
  }

  @NotNull
  @Override
  public Icon getIcon() {
    if (myDevices.size() == 1) {
      return myDevices.get(0).getIcon();
    }

    // TODO Should we use a better icon?
    return StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE;
  }

  @Override
  public boolean canRun(@NotNull RunConfiguration configuration) {
    return true;
  }
}
