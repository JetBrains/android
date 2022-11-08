/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.devicemanager.AdbShellCommandExecutor;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.StorageDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncDetailsBuilder {
  private final @Nullable Project myProject;
  private final @NotNull PhysicalDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull AdbShellCommandExecutor myExecutor;

  AsyncDetailsBuilder(@Nullable Project project, @NotNull PhysicalDevice device) {
    this(project, device, new DeviceManagerAndroidDebugBridge(), new AdbShellCommandExecutor());
  }

  @VisibleForTesting
  AsyncDetailsBuilder(@Nullable Project project,
                      @NotNull PhysicalDevice device,
                      @NotNull DeviceManagerAndroidDebugBridge bridge,
                      @NotNull AdbShellCommandExecutor executor) {
    myProject = project;
    myDevice = device;
    myBridge = bridge;
    myExecutor = executor;
  }

  @NotNull ListenableFuture<@NotNull PhysicalDevice> buildAsync() {
    Executor executor = AppExecutorUtil.getAppExecutorService();

    // noinspection UnstableApiUsage
    return FluentFuture.from(myBridge.getDevices(myProject))
      .transform(this::findDevice, executor)
      .transform(this::build, executor);
  }

  private @NotNull IDevice findDevice(@NotNull Collection<@NotNull IDevice> devices) {
    Object key = myDevice.getKey().toString();

    Optional<IDevice> optionalDevice = devices.stream()
      .filter(device -> device.getSerialNumber().equals(key))
      .findFirst();

    return optionalDevice.orElseThrow(AssertionError::new);
  }

  private @NotNull PhysicalDevice build(@NotNull IDevice device) {
    return new PhysicalDevice.Builder()
      .setKey(myDevice.getKey())
      .setName(myDevice.getName())
      .setTarget(myDevice.getTarget())
      .setAndroidVersion(myDevice.getAndroidVersion())
      .setPower(myExecutor.execute(device, "dumpsys battery").flatMap(Battery::newBattery).orElse(null))
      .setResolution(myExecutor.execute(device, "wm size").flatMap(Resolution.Companion::newResolution).orElse(null))
      .setDensity(device.getDensity())
      .addAllAbis(device.getAbis())
      .setStorageDevice(myExecutor.execute(device, "df /data").flatMap(StorageDevice::newStorageDevice).orElse(null))
      .build();
  }

  @Nullable Project getProject() {
    return myProject;
  }
}
