/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.AvdData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.Key;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceChangeListener implements IDeviceChangeListener {
  private final @NotNull VirtualDeviceTableModel myModel;
  private final @NotNull NewSetOnline myNewSetOnline;

  @VisibleForTesting
  interface NewSetOnline {
    @NotNull FutureCallback<@Nullable Key> apply(@NotNull VirtualDeviceTableModel model, boolean online);
  }

  @UiThread
  VirtualDeviceChangeListener(@NotNull VirtualDeviceTableModel model) {
    this(model, VirtualDeviceChangeListener::newSetOnline);
  }

  @VisibleForTesting
  VirtualDeviceChangeListener(@NotNull VirtualDeviceTableModel model, @NotNull NewSetOnline newSetOnline) {
    myModel = model;
    myNewSetOnline = newSetOnline;
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @VisibleForTesting
  static @NotNull FutureCallback<@Nullable Key> newSetOnline(@NotNull VirtualDeviceTableModel model, boolean online) {
    return new DeviceManagerFutureCallback<>(VirtualDeviceChangeListener.class, key -> {
      if (key == null) {
        model.setAllOnline();
      }
      else {
        model.setOnline(key, online);
      }
    });
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceConnected(@NotNull IDevice device) {
    if (!device.isEmulator()) {
      return;
    }

    Logger.getInstance(VirtualDeviceChangeListener.class).info(device + " connected");
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (!device.isEmulator()) {
      return;
    }

    Logger.getInstance(VirtualDeviceChangeListener.class).info(device + " disconnected");
  }

  /**
   * Called by the device list monitor and the device client monitor threads
   */
  @WorkerThread
  @Override
  public void deviceChanged(@NotNull IDevice device, int mask) {
    if (!device.isEmulator()) {
      return;
    }

    if ((mask & IDevice.CHANGE_STATE) == 0) {
      return;
    }

    DeviceState state = device.getState();
    Logger.getInstance(VirtualDeviceChangeListener.class).info(device + " state changed to " + state);

    if (state == null) {
      return;
    }

    switch (state) {
      case OFFLINE:
        setOnline(device, false);
        break;
      case ONLINE:
        setOnline(device, true);
        break;
      default:
        break;
    }
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  private void setOnline(@NotNull IDevice device, boolean online) {
    Executor executor = EdtExecutorService.getInstance();

    // noinspection UnstableApiUsage
    FluentFuture.from(device.getAvdData())
      .transform(VirtualDeviceChangeListener::getKey, executor)
      .addCallback(myNewSetOnline.apply(myModel, online), executor);
  }

  @UiThread
  private static @Nullable Key getKey(@Nullable AvdData avd) {
    if (avd == null) {
      return null;
    }

    String name = avd.getName();

    if (name == null) {
      return null;
    }

    return new VirtualDeviceName(name);
  }
}
