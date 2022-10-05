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

import static com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxAction.DEPLOYS_TO_LOCAL_DEVICE;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.UserDataHolderBase;
import icons.StudioIcons;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * The combo box generates these {@link ExecutionTarget ExecutionTargets.} ExecutionTargets determine the state of the run, debug, and stop
 * (but <em>not</em> the apply changes) toolbar buttons.
 */
final class DeviceAndSnapshotComboBoxExecutionTarget extends AndroidExecutionTarget {
  private final @NotNull Collection<@NotNull Key> myKeys;
  private final @NotNull AsyncDevicesGetter myDevicesGetter;

  DeviceAndSnapshotComboBoxExecutionTarget(@NotNull Collection<@NotNull Target> targets, @NotNull AsyncDevicesGetter devicesGetter) {
    myKeys = targets.stream()
      .map(Target::getDeviceKey)
      .collect(Collectors.toSet());

    myDevicesGetter = devicesGetter;
  }

  @Override
  public boolean isApplicationRunning(@NotNull String appPackage) {
    return deviceStream().anyMatch(device -> device.isRunning(appPackage));
  }

  @Override
  public int getAvailableDeviceCount() {
    return (int)deviceStream().count();
  }

  @NotNull
  @Override
  public Collection<IDevice> getRunningDevices() {
    return deviceStream()
      .map(Device::getDdmlibDevice)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private @NotNull Stream<@NotNull Device> deviceStream() {
    return myDevicesGetter.get().map(this::filteredStream).orElseGet(Stream::empty);
  }

  private @NotNull Stream<@NotNull Device> filteredStream(@NotNull Collection<@NotNull Device> devices) {
    return devices.stream().filter(device -> myKeys.contains(device.getKey()));
  }

  @NotNull
  @Override
  public String getId() {
    return myKeys.stream()
      .sorted()
      .map(Key::toString)
      .collect(Collectors.joining(", ", "device_and_snapshot_combo_box_target[", "]"));
  }

  @NotNull
  @Override
  public String getDisplayName() {
    List<Device> devices = deviceStream().collect(Collectors.toList());

    switch (devices.size()) {
      case 0:
        return "No Devices";
      case 1:
        return devices.get(0).getName();
      default:
        return "Multiple Devices";
    }
  }

  @NotNull
  @Override
  public Icon getIcon() {
    List<Device> devices = deviceStream().collect(Collectors.toList());

    if (devices.size() == 1) {
      return devices.get(0).getIcon();
    }

    return StudioIcons.DeviceExplorer.MULTIPLE_DEVICES;
  }

  @Override
  public boolean canRun(@NotNull RunConfiguration configuration) {
    Boolean deploysToLocalDevice = false;
    // This allows BlazeCommandRunConfiguration to run as its DEPLOY_TO_LOCAL_DEVICE is set by BlazeAndroidBinaryRunConfigurationHandler
    if (configuration instanceof UserDataHolderBase) {
      deploysToLocalDevice = ((UserDataHolderBase)configuration).getUserData(DEPLOYS_TO_LOCAL_DEVICE);
    }
    return configuration instanceof AndroidRunConfigurationBase || (deploysToLocalDevice != null && deploysToLocalDevice);
  }
}
