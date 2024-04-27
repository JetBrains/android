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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.DeployableToDevice;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.util.concurrency.AppExecutorUtil;
import icons.StudioIcons;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The combo box generates these {@link ExecutionTarget ExecutionTargets.} ExecutionTargets determine the state of the run, debug, and stop
 * (but <em>not</em> the apply changes) toolbar buttons.
 */
public final class DeviceAndSnapshotComboBoxExecutionTarget extends AndroidExecutionTarget {
  private final @NotNull Collection<Key> myKeys;
  private final @NotNull AsyncDevicesGetter myDevicesGetter;

  @NotNull
  private final Supplier<DeploymentApplicationService> myDeploymentApplicationServiceGetInstance;

  public DeviceAndSnapshotComboBoxExecutionTarget(@NotNull Collection<Target> targets, @NotNull AsyncDevicesGetter devicesGetter) {
    this(targets, devicesGetter, DeploymentApplicationService::getInstance);
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxExecutionTarget(@NotNull Collection<Target> targets,
                                           @NotNull AsyncDevicesGetter devicesGetter,
                                           @NotNull Supplier<DeploymentApplicationService> deploymentApplicationServiceGetInstance) {
    myKeys = targets.stream()
      .map(Target::getDeviceKey)
      .collect(Collectors.toSet());

    myDevicesGetter = devicesGetter;
    myDeploymentApplicationServiceGetInstance = deploymentApplicationServiceGetInstance;
  }

  @Override
  public @NotNull ListenableFuture<Boolean> isApplicationRunningAsync(@NotNull String appPackage) {
    return Futures.submit(() -> isApplicationRunning(appPackage), AppExecutorUtil.getAppExecutorService());
  }

  private boolean isApplicationRunning(@NotNull String appPackage) {
    var service = myDeploymentApplicationServiceGetInstance.get();

    return getRunningDevices().stream()
      .map(device -> service.findClient(device, appPackage))
      .anyMatch(clients -> !clients.isEmpty());
  }

  @Override
  public int getAvailableDeviceCount() {
    return (int)deviceStream().count();
  }

  @NotNull
  @Override
  public Collection<IDevice> getRunningDevices() {
    return deviceStream()
      .filter(Device::connected)
      .map(Device::ddmlibDeviceAsync)
      .map(Futures::getUnchecked)
      .collect(Collectors.toList());
  }

  private @NotNull Stream<Device> deviceStream() {
    return myDevicesGetter.get().map(this::filteredStream).orElseGet(Stream::empty);
  }

  private @NotNull Stream<Device> filteredStream(@NotNull Collection<Device> devices) {
    return devices.stream().filter(device -> myKeys.contains(device.key()));
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
    var devices = deviceStream().toList();

    return switch (devices.size()) {
      case 0 -> "No Devices";
      case 1 -> devices.get(0).name();
      default -> "Multiple Devices";
    };
  }

  @NotNull
  @Override
  public Icon getIcon() {
    var devices = deviceStream().toList();

    if (devices.size() == 1) {
      return devices.get(0).icon();
    }

    return StudioIcons.DeviceExplorer.MULTIPLE_DEVICES;
  }

  @Override
  public boolean canRun(@NotNull RunConfiguration configuration) {
    return DeployableToDevice.deploysToLocalDevice(configuration);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof DeviceAndSnapshotComboBoxExecutionTarget target)) {
      return false;
    }

    return myKeys.equals(target.myKeys) &&
           myDevicesGetter.equals(target.myDevicesGetter) &&
           myDeploymentApplicationServiceGetInstance.equals(target.myDeploymentApplicationServiceGetInstance);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myKeys, myDevicesGetter, myDeploymentApplicationServiceGetInstance);
  }
}
