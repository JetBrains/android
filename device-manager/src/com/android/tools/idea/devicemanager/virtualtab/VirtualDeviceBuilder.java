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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.annotations.concurrency.WorkerThread;
import com.android.repository.io.FileUtilKt;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Targets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

final class VirtualDeviceBuilder {
  private final @NotNull AvdInfo myDevice;
  private final @NotNull Supplier<Boolean> myIsAvdRunning;
  private final @NotNull Supplier<Long> myRecursiveSize;

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  VirtualDeviceBuilder(@NotNull AvdInfo device) {
    this(device, () -> AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(device), () -> recursiveSize(device));
  }

  @VisibleForTesting
  VirtualDeviceBuilder(@NotNull AvdInfo device,
                       @NotNull Supplier<Boolean> isAvdRunning,
                       @NotNull Supplier<Long> recursiveSize) {
    myDevice = device;
    myIsAvdRunning = isAvdRunning;
    myRecursiveSize = recursiveSize;
  }

  private static long recursiveSize(@NotNull AvdInfo device) {
    try {
      return FileUtilKt.recursiveSize(device.getDataFolderPath());
    }
    catch (IOException exception) {
      Logger.getInstance(VirtualDeviceBuilder.class).warn(exception);
      return 0;
    }
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  @NotNull VirtualDevice build() {
    IdDisplay tag = myDevice.getTag();
    AndroidVersion version = myDevice.getAndroidVersion();

    VirtualDevice.Builder builder = new VirtualDevice.Builder()
      .setKey(new VirtualDevicePath(myDevice.getId()))
      .setType(getType(tag))
      .setName(myDevice.getDisplayName())
      .setTarget(Targets.toString(version, tag))
      .setCpuArchitecture(myDevice.getCpuArch())
      .setAndroidVersion(version)
      .setSizeOnDisk(myRecursiveSize.get())
      .setState(VirtualDevice.State.valueOf(myIsAvdRunning.get()))
      .setAvdInfo(myDevice);

    if (AvdManagerConnection.isSystemImageDownloadProblem(myDevice.getStatus())) {
      builder.setIcon(AllIcons.Actions.Download);
    }

    return builder
      .build();
  }

  private static @NotNull DeviceType getType(@NotNull IdDisplay tag) {
    if (tag.equals(SystemImage.WEAR_TAG)) {
      return DeviceType.WEAR_OS;
    }
    else if (tag.equals(SystemImage.ANDROID_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.GOOGLE_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_PLAY_STORE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else {
      return DeviceType.PHONE;
    }
  }
}
