/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ConnectedDevicesTask implements Callable<Collection<ConnectedDevice>> {
  @NotNull
  private final Project myProject;

  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  @NotNull
  private final Function<Project, Stream<IDevice>> myGetDdmlibDevices;

  ConnectedDevicesTask(@NotNull Project project, @Nullable LaunchCompatibilityChecker checker) {
    this(project, checker, ConnectedDevicesTask::getDdmlibDevices);
  }

  @VisibleForTesting
  ConnectedDevicesTask(@NotNull Project project,
                       @Nullable LaunchCompatibilityChecker checker,
                       @NotNull Function<Project, Stream<IDevice>> getDdmlibDevices) {
    myProject = project;
    myChecker = checker;
    myGetDdmlibDevices = getDdmlibDevices;
  }

  @NotNull
  @Override
  public Collection<ConnectedDevice> call() {
    return myGetDdmlibDevices.apply(myProject)
      .filter(IDevice::isOnline)
      .map(this::newConnectedDevice)
      .collect(Collectors.toList());
  }

  @NotNull
  private static Stream<IDevice> getDdmlibDevices(@NotNull Project project) {
    File adb = AndroidSdkUtils.getAdb(project);

    if (adb == null) {
      return Stream.empty();
    }

    Future<AndroidDebugBridge> futureBridge = AdbService.getInstance().getDebugBridge(adb);

    if (!futureBridge.isDone()) {
      return Stream.empty();
    }

    try {
      return Arrays.stream(futureBridge.get().getDevices());
    }
    catch (InterruptedException exception) {
      // This should never happen. The future is done and can no longer be interrupted.
      throw new AssertionError(exception);
    }
    catch (ExecutionException exception) {
      Logger.getInstance(ConnectedDevicesTask.class).warn(exception);
      return Stream.empty();
    }
  }

  @NotNull
  private ConnectedDevice newConnectedDevice(@NotNull IDevice ddmlibDevice) {
    AndroidDevice androidDevice = new ConnectedAndroidDevice(ddmlibDevice, null);

    ConnectedDevice.Builder builder = new ConnectedDevice.Builder()
      .setKey(ddmlibDevice.getSerialNumber())
      .setAndroidDevice(androidDevice);

    if (myChecker == null) {
      return builder.build();
    }

    LaunchCompatibility compatibility = myChecker.validate(androidDevice);

    return builder
      .setValid(!compatibility.isCompatible().equals(ThreeState.NO))
      .setValidityReason(compatibility.getReason())
      .build();
  }
}
