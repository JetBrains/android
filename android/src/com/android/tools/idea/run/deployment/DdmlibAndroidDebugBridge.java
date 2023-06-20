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

import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DdmlibAndroidDebugBridge implements AndroidDebugBridge {
  private final @NotNull ListeningExecutorService myExecutorService;
  private final @NotNull Supplier<Path> myGetAdb;
  private final @NotNull AsyncFunction<Path, com.android.ddmlib.AndroidDebugBridge> myGetDebugBridge;

  DdmlibAndroidDebugBridge(@NotNull Project project) {
    this(MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()),
         () -> getAdb(project),
         DdmlibAndroidDebugBridge::getDebugBridge);
  }

  @VisibleForTesting
  DdmlibAndroidDebugBridge(@NotNull ListeningExecutorService executorService,
                           @NotNull Supplier<Path> getAdb,
                           @NotNull AsyncFunction<Path, com.android.ddmlib.AndroidDebugBridge> getDebugBridge) {
    myExecutorService = executorService;
    myGetAdb = getAdb;
    myGetDebugBridge = getDebugBridge;
  }

  @WorkerThread
  private static @Nullable Path getAdb(@NotNull Project project) {
    return Optional.ofNullable(AndroidSdkUtils.getAdb(project)).map(File::toPath).orElse(null);
  }

  @WorkerThread
  private static @NotNull ListenableFuture<com.android.ddmlib.AndroidDebugBridge> getDebugBridge(@Nullable Path adb) {
    return adb == null ? Futures.immediateFuture(null) : AdbService.getInstance().getDebugBridge(adb.toFile());
  }

  @Override
  public @NotNull ListenableFuture<Collection<IDevice>> getConnectedDevices() {
    // noinspection UnstableApiUsage
    return FluentFuture.from(myExecutorService.submit(myGetAdb::get))
      .transformAsync(myGetDebugBridge, myExecutorService)
      .transform(DdmlibAndroidDebugBridge::getDevices, myExecutorService);
  }

  @WorkerThread
  private static @NotNull Collection<IDevice> getDevices(com.android.ddmlib.AndroidDebugBridge bridge) {
    if (bridge == null) {
      return Collections.emptyList();
    }

    if (!bridge.isConnected()) {
      return Collections.emptyList();
    }

    return Arrays.asList(bridge.getDevices());
  }
}
