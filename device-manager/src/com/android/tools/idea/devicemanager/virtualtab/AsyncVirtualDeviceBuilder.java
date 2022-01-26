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

import com.android.annotations.concurrency.UiThread;
import com.android.repository.io.FileUtilKt;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.Targets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

final class AsyncVirtualDeviceBuilder {
  private final @NotNull AvdInfo myDevice;
  private final @NotNull ListenableFuture<@NotNull Long> mySizeOnDiskFuture;

  @UiThread
  AsyncVirtualDeviceBuilder(@NotNull AvdInfo device, @NotNull ListeningExecutorService service) {
    this(device, service.submit(() -> recursiveSize(device)));
  }

  @VisibleForTesting
  AsyncVirtualDeviceBuilder(@NotNull AvdInfo device, @NotNull ListenableFuture<@NotNull Long> sizeOnDiskFuture) {
    myDevice = device;
    mySizeOnDiskFuture = sizeOnDiskFuture;
  }

  private static long recursiveSize(@NotNull AvdInfo device) {
    try {
      return FileUtilKt.recursiveSize(device.getDataFolderPath());
    }
    catch (IOException exception) {
      Logger.getInstance(AsyncVirtualDeviceBuilder.class).warn(exception);
      return 0;
    }
  }

  @UiThread
  @NotNull ListenableFuture<@NotNull VirtualDevice> buildAsync() {
    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(mySizeOnDiskFuture).call(this::build, EdtExecutorService.getInstance());
  }

  @UiThread
  private @NotNull VirtualDevice build() {
    IdDisplay tag = myDevice.getTag();
    AndroidVersion version = myDevice.getAndroidVersion();

    return new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName(myDevice.getName()))
      .setType(getType(tag))
      .setName(myDevice.getDisplayName())
      .setTarget(Targets.toString(version, tag))
      .setCpuArchitecture(myDevice.getCpuArch())
      .setApi(Integer.toString(version.getApiLevel()))
      .setSizeOnDisk(DeviceManagerFutures.getDoneOrElse(mySizeOnDiskFuture, 0L))
      .setResolution(getResolution(myDevice))
      .setDensity(getDensity(myDevice))
      .setAvdInfo(myDevice)
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

  private static @Nullable Resolution getResolution(@NotNull AvdInfo device) {
    String width = device.getProperty("hw.lcd.width");

    if (width == null) {
      return null;
    }

    String height = device.getProperty("hw.lcd.height");

    if (height == null) {
      return null;
    }

    try {
      return new Resolution(Integer.parseInt(width), Integer.parseInt(height));
    }
    catch (NumberFormatException exception) {
      return null;
    }
  }

  private static int getDensity(@NotNull AvdInfo device) {
    String density = device.getProperty("hw.lcd.density");

    if (density == null) {
      return -1;
    }

    try {
      return Integer.parseInt(density);
    }
    catch (NumberFormatException exception) {
      return -1;
    }
  }
}
