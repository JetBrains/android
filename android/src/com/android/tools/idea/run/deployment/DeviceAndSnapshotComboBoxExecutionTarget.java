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
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.configuration.AndroidWearConfiguration;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.AppExecutorUtil;
import icons.StudioIcons;
import java.awt.EventQueue;
import java.util.Collection;
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
  private final @NotNull Collection<Key> myKeys;
  private final @NotNull AsyncDevicesGetter myDevicesGetter;

  DeviceAndSnapshotComboBoxExecutionTarget(@NotNull Collection<Target> targets, @NotNull AsyncDevicesGetter devicesGetter) {
    myKeys = targets.stream()
      .map(Target::getDeviceKey)
      .collect(Collectors.toSet());

    myDevicesGetter = devicesGetter;
  }

  @Override
  public @NotNull ListenableFuture<Boolean> isApplicationRunningAsync(@NotNull String appPackage) {
    var futures = deviceStream()
      .map(device -> device.isRunningAsync(appPackage))
      .collect(Collectors.toList());

    // noinspection UnstableApiUsage, SpellCheckingInspection
    return Futures.transform(Futures.successfulAsList(futures), runnings -> runnings.contains(true),
                             AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public int getAvailableDeviceCount() {
    return (int)deviceStream().count();
  }

  @Override
  public @NotNull ListenableFuture<Collection<IDevice>> getRunningDevicesAsync() {
    var futures = deviceStream()
      .filter(Device::isConnected)
      .map(Device::getDdmlibDeviceAsync)
      .collect(Collectors.toList());

    @SuppressWarnings("UnstableApiUsage")
    var future = Futures.successfulAsList(futures);

    // The EDT and Action Updater (Common) threads call into this. Ideally we'd use the respective executors here instead of the direct
    // executor. But we don't have access to the Action Updater (Common) executor.

    // noinspection UnstableApiUsage
    return Futures.transform(future, DeviceAndSnapshotComboBoxExecutionTarget::filterNonNull, MoreExecutors.directExecutor());
  }

  @NotNull
  @Override
  public Collection<IDevice> getRunningDevices() {
    if (Thread.currentThread().getName().equals("Action Updater (Common)") || EventQueue.isDispatchThread()) {
      Loggers.errorConditionally(DeviceAndSnapshotComboBoxExecutionTarget.class,
                                 "Blocking Future::get calls on an Action Updater (Common) thread or the EDT http://b/259746412, " +
                                 "http://b/259746444, http://b/259746749, http://b/259747002, http://b/259747870, and http://b/259747965");
    }

    return deviceStream()
      .filter(Device::isConnected)
      .map(Device::getDdmlibDeviceAsync)
      .map(Futures::getUnchecked)
      .collect(Collectors.toList());
  }

  private @NotNull Stream<Device> deviceStream() {
    return myDevicesGetter.get().map(this::filteredStream).orElseGet(Stream::empty);
  }

  private @NotNull Stream<Device> filteredStream(@NotNull Collection<Device> devices) {
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
    var devices = deviceStream().toList();

    return switch (devices.size()) {
      case 0 -> "No Devices";
      case 1 -> devices.get(0).getName();
      default -> "Multiple Devices";
    };
  }

  @NotNull
  @Override
  public Icon getIcon() {
    var devices = deviceStream().toList();

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
      deploysToLocalDevice = ((UserDataHolderBase)configuration).getUserData(DeviceAndSnapshotComboBoxAction.DEPLOYS_TO_LOCAL_DEVICE);
    }
    return configuration instanceof AndroidRunConfigurationBase ||
           configuration instanceof AndroidWearConfiguration ||
           (deploysToLocalDevice != null && deploysToLocalDevice);
  }

  private static @NotNull Collection<IDevice> filterNonNull(@NotNull Collection<IDevice> devices) {
    return devices.stream()
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
