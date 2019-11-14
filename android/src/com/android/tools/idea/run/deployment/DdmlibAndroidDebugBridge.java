/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.adb.AdbService;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

final class DdmlibAndroidDebugBridge implements AndroidDebugBridge {
  @NotNull
  private final File myAdb;

  @NotNull
  private final ListeningExecutorService myListeningExecutorService;

  DdmlibAndroidDebugBridge(@NotNull File adb) {
    myAdb = adb;
    myListeningExecutorService = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  @Override
  public ListenableFuture<Collection<IDevice>> getConnectedDevices() {
    // noinspection UnstableApiUsage
    return FluentFuture.from(AdbService.getInstance().getDebugBridge(myAdb))
      .transform(com.android.ddmlib.AndroidDebugBridge::getDevices, myListeningExecutorService)
      .transform(Arrays::asList, myListeningExecutorService);
  }

  @NotNull
  @Override
  public ListenableFuture<String> getVirtualDeviceId(@NotNull IDevice virtualDevice) {
    return com.android.ddmlib.AndroidDebugBridge.getVirtualDeviceId(myListeningExecutorService, myAdb, virtualDevice);
  }
}
