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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.adb.AdbShellCommandResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AsyncDetailsBuilder {
  private final @Nullable Project myProject;
  private final @NotNull PhysicalDevice myDevice;
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge;
  private final @NotNull AdbShellCommandExecutor myExecutor;

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
      .setApi(myDevice.getApi())
      .setResolution(getResolution(device))
      .setDensity(device.getDensity())
      .addAllAbis(device.getAbis())
      .build();
  }

  private @Nullable Resolution getResolution(@NotNull IDevice device) {
    try {
      return newResolution(myExecutor.execute(device, "wm size"));
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException exception) {
      Logger.getInstance(AsyncDetailsBuilder.class).warn(exception);
      return null;
    }
  }

  private static @Nullable Resolution newResolution(@NotNull AdbShellCommandResult result) {
    List<String> output = result.getOutput();

    if (result.isError()) {
      String separator = System.lineSeparator();

      StringBuilder builder = new StringBuilder("Command failed:")
        .append(separator);

      output.forEach(line -> builder.append(line).append(separator));

      Logger.getInstance(AsyncDetailsBuilder.class).warn(builder.toString());
      return null;
    }

    return Resolution.newResolution(output.get(0));
  }
}
