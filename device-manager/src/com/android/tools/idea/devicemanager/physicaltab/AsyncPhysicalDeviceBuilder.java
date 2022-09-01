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

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.Targets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

final class AsyncPhysicalDeviceBuilder {
  private final @NotNull IDevice myDevice;
  private final @NotNull Key myKey;

  private final @NotNull ListenableFuture<@NotNull AndroidVersion> myVersionFuture;
  private final @NotNull ListenableFuture<@NotNull DeviceType> myTypeFuture;
  private final @NotNull ListenableFuture<@NotNull String> myModelFuture;
  private final @NotNull ListenableFuture<@NotNull String> myManufacturerFuture;

  @UiThread
  AsyncPhysicalDeviceBuilder(@NotNull IDevice device, @NotNull Key key) {
    myDevice = device;
    myKey = key;

    myVersionFuture = DeviceManagerFutures.appExecutorServiceSubmit(device::getVersion);
    myTypeFuture = DeviceManagerFutures.appExecutorServiceSubmit(this::getType);
    myModelFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL);
    myManufacturerFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private DeviceType getType() {
    String string = myDevice.getProperty(IDevice.PROP_BUILD_CHARACTERISTICS);

    if (string == null) {
      return DeviceType.PHONE;
    }

    Collection<String> collection = Arrays.asList(string.split(","));

    if (collection.contains(HardwareFeature.WATCH.getCharacteristic())) {
      return DeviceType.WEAR_OS;
    }

    if (collection.contains(HardwareFeature.TV.getCharacteristic())) {
      return DeviceType.TV;
    }

    if (collection.contains(HardwareFeature.AUTOMOTIVE.getCharacteristic())) {
      return DeviceType.AUTOMOTIVE;
    }

    return DeviceType.PHONE;
  }

  @UiThread
  @NotNull ListenableFuture<@NotNull PhysicalDevice> buildAsync() {
    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(myVersionFuture, myTypeFuture, myModelFuture, myManufacturerFuture)
      .call(this::build, EdtExecutorService.getInstance());
  }

  @UiThread
  private @NotNull PhysicalDevice build() {
    AndroidVersion version = DeviceManagerFutures.getDoneOrElse(myVersionFuture, AndroidVersion.DEFAULT);

    PhysicalDevice.Builder builder = new PhysicalDevice.Builder()
      .setKey(myKey)
      .setType(DeviceManagerFutures.getDoneOrElse(myTypeFuture, DeviceType.PHONE))
      .setName(DeviceNameProperties.getName(FutureUtils.getDoneOrNull(myModelFuture), FutureUtils.getDoneOrNull(myManufacturerFuture)))
      .setTarget(Targets.toString(version))
      .setAndroidVersion(version);

    if (myDevice.isOnline()) {
      builder.addConnectionType(myKey.getConnectionType());
    }

    return builder.build();
  }
}
