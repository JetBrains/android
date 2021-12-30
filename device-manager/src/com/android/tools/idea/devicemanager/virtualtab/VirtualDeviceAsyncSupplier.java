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
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerFutures;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.List;
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
  @NotNull ListenableFuture<@NotNull List<@NotNull VirtualDevice>> get() {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());

    Iterable<ListenableFuture<VirtualDevice>> futures = myGetAvds.get().stream()
      .map(device -> new AsyncVirtualDeviceBuilder(device, service))
      .map(AsyncVirtualDeviceBuilder::buildAsync)
      .collect(Collectors.toList());

    return DeviceManagerFutures.successfulAsList(futures, EdtExecutorService.getInstance());
  }
}
