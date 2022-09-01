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
import com.android.annotations.concurrency.WorkerThread;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.android.tools.idea.devicemanager.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

final class VirtualDeviceAsyncSupplier {
  private final @NotNull Supplier<@NotNull List<@NotNull AvdInfo>> myGetAvds;

  @UiThread
  VirtualDeviceAsyncSupplier() {
    this(() -> AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true));
  }

  @VisibleForTesting
  VirtualDeviceAsyncSupplier(@NotNull Supplier<@NotNull List<@NotNull AvdInfo>> getAvds) {
    myGetAvds = getAvds;
  }

  @UiThread
  @NotNull ListenableFuture<@NotNull List<@NotNull VirtualDevice>> getAll() {
    return DeviceManagerFutures.appExecutorServiceSubmit(this::buildAll);
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull List<@NotNull VirtualDevice> buildAll() {
    return myGetAvds.get().stream()
      .map(VirtualDeviceBuilder::new)
      .map(VirtualDeviceBuilder::build)
      .collect(Collectors.toList());
  }

  @UiThread
  @NotNull ListenableFuture<@NotNull VirtualDevice> get(@NotNull Key key) {
    return DeviceManagerFutures.appExecutorServiceSubmit(() -> build(key));
  }

  /**
   * Called by an application pool thread
   */
  @WorkerThread
  private @NotNull VirtualDevice build(@NotNull Key key) {
    Optional<VirtualDevice> device = myGetAvds.get().stream()
      .filter(avd -> avd.getId().equals(key.toString()))
      .map(VirtualDeviceBuilder::new)
      .map(VirtualDeviceBuilder::build)
      .findFirst();

    return device.orElseThrow(() -> new NoSuchElementException(key.toString()));
  }
}
