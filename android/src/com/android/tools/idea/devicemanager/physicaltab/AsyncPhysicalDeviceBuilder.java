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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.adb.AdbShellCommandResult;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.util.Targets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncPhysicalDeviceBuilder {
  private final @NotNull IDevice myDevice;
  private final @NotNull Key myKey;
  private final @Nullable Instant myLastOnlineTime;
  private final @NotNull AdbShellCommandExecutor myExecutor;

  private final @NotNull ListenableFuture<@NotNull String> myModelFuture;
  private final @NotNull ListenableFuture<@NotNull String> myManufacturerFuture;
  private final @NotNull ListenableFuture<@Nullable Resolution> myResolutionFuture;

  AsyncPhysicalDeviceBuilder(@NotNull IDevice device, @NotNull Key key, @Nullable Instant lastOnlineTime) {
    this(device, key, lastOnlineTime, new AdbShellCommandExecutor());
  }

  @VisibleForTesting
  AsyncPhysicalDeviceBuilder(@NotNull IDevice device,
                             @NotNull Key key,
                             @Nullable Instant lastOnlineTime,
                             @NotNull AdbShellCommandExecutor executor) {
    myDevice = device;
    myKey = key;
    myLastOnlineTime = lastOnlineTime;
    myExecutor = executor;

    myModelFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MODEL);
    myManufacturerFuture = device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER);
    myResolutionFuture = getResolution(device);
  }

  private @NotNull ListenableFuture<@Nullable Resolution> getResolution(@NotNull IDevice device) {
    ExecutorService service = AppExecutorUtil.getAppExecutorService();

    // noinspection UnstableApiUsage
    return FluentFuture.from(MoreExecutors.listeningDecorator(service).submit(() -> myExecutor.execute(device, "wm size")))
      .transform(AsyncPhysicalDeviceBuilder::newResolution, EdtExecutorService.getInstance());
  }

  private static @Nullable Resolution newResolution(@NotNull AdbShellCommandResult result) {
    List<String> output = result.getOutput();

    if (result.isError()) {
      String separator = System.lineSeparator();

      StringBuilder builder = new StringBuilder("Command failed:")
        .append(separator);

      output.forEach(line -> builder.append(line).append(separator));

      Logger.getInstance(AsyncPhysicalDeviceBuilder.class).warn(builder.toString());
      return null;
    }

    return Resolution.newResolution(output.get(0));
  }

  @NotNull ListenableFuture<@NotNull PhysicalDevice> buildAsync() {
    // noinspection UnstableApiUsage
    return Futures.whenAllComplete(myModelFuture, myManufacturerFuture, myResolutionFuture)
      .call(this::build, EdtExecutorService.getInstance());
  }

  private @NotNull PhysicalDevice build() {
    AndroidVersion version = myDevice.getVersion();

    PhysicalDevice.Builder builder = new PhysicalDevice.Builder()
      .setKey(myKey)
      .setLastOnlineTime(myLastOnlineTime)
      .setName(DeviceNameProperties.getName(FutureUtils.getDoneOrNull(myModelFuture), FutureUtils.getDoneOrNull(myManufacturerFuture)))
      .setTarget(Targets.toString(version))
      .setApi(version.getApiString());

    if (myDevice.isOnline()) {
      builder.addConnectionType(myKey.getConnectionType());
    }

    return builder
      .setResolution(FutureUtils.getDoneOrNull(myResolutionFuture))
      .build();
  }
}
