/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.AdbShellCommandExecutor;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.devicemanager.StorageDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.OptionalInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncVirtualDeviceDetailsBuilder {
  private final @Nullable Project myProject;
  private final @NotNull VirtualDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull AdbShellCommandExecutor myAdbShellCommandExecutor;

  AsyncVirtualDeviceDetailsBuilder(@Nullable Project project, @NotNull VirtualDevice device) {
    this(project, device, new DeviceManagerAndroidDebugBridge(), new AdbShellCommandExecutor());
  }

  @VisibleForTesting
  AsyncVirtualDeviceDetailsBuilder(@Nullable Project project,
                                   @NotNull VirtualDevice device,
                                   @NotNull DeviceManagerAndroidDebugBridge bridge,
                                   @NotNull AdbShellCommandExecutor adbShellCommandExecutor) {
    myProject = project;
    myDevice = device;
    myBridge = bridge;
    myAdbShellCommandExecutor = adbShellCommandExecutor;
  }

  @NotNull ListenableFuture<Device> buildAsync() {
    // noinspection UnstableApiUsage
    return Futures.transform(myBridge.findDevice(myProject, myDevice.getKey()), this::build, AppExecutorUtil.getAppExecutorService());
  }

  private @NotNull Device build(@Nullable IDevice device) {
    AvdInfo avd = myDevice.getAvdInfo();

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setKey(myDevice.getKey())
      .setName(myDevice.getName())
      .setTarget(myDevice.getTarget())
      .setCpuArchitecture(myDevice.getCpuArchitecture())
      .setResolution(getResolution(avd))
      .setDensity(getProperty(avd, "hw.lcd.density").orElse(-1));

    if (device != null) {
      builder
        .addAllAbis(device.getAbis())
        .setStorageDevice(myAdbShellCommandExecutor.execute(device, "df /data").flatMap(StorageDevice::newStorageDevice).orElse(null));
    }

    return builder
      .setAvdInfo(avd)
      .build();
  }

  private static @Nullable Resolution getResolution(@NotNull AvdInfo avd) {
    OptionalInt width = getProperty(avd, "hw.lcd.width");
    OptionalInt height = getProperty(avd, "hw.lcd.height");

    if (width.isEmpty() || height.isEmpty()) {
      return null;
    }

    return new Resolution(width.orElseThrow(), height.orElseThrow());
  }

  private static @NotNull OptionalInt getProperty(@NotNull AvdInfo avd, @NotNull String name) {
    String property = avd.getProperty(name);

    if (property == null) {
      return OptionalInt.empty();
    }

    try {
      return OptionalInt.of(Integer.parseInt(property));
    }
    catch (NumberFormatException exception) {
      return OptionalInt.empty();
    }
  }

  @Nullable Project getProject() {
    return myProject;
  }
}
