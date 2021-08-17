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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public final class VirtualDeviceModel {
  private final @NotNull List<@NotNull AvdInfo> myAvds;
  private final @NotNull Supplier<@NotNull List<@NotNull AvdInfo>> myAvdsSupplier;
  private final @NotNull List<@NotNull VirtualDeviceModelListener> myListeners;

  VirtualDeviceModel() {
    this(() -> AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true));
  }

  @VisibleForTesting
  VirtualDeviceModel(@NotNull Supplier<@NotNull List<@NotNull AvdInfo>> avdsSupplier) {
    myAvds = new ArrayList<>();
    myAvdsSupplier = avdsSupplier;
    myListeners = new ArrayList<>();
  }

  public void refreshAvds() {
    myAvds.clear();
    myAvds.addAll(myAvdsSupplier.get());

    logVirtualDeviceCount();

    MoreExecutors.directExecutor().execute(() -> myListeners.forEach(
      listener -> listener.avdListChanged(myAvds)));
  }

  private void logVirtualDeviceCount() {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.VIRTUAL_DEVICE_COUNT)
      .setVirtualDeviceCount(myAvds.size())
      .build();

    DeviceManagerUsageTracker.log(event);
  }

  void addListener(@NotNull VirtualDeviceModelListener listener) {
    myListeners.add(listener);
  }

  interface VirtualDeviceModelListener {
    void avdListChanged(@NotNull List<AvdInfo> avds);
  }
}
