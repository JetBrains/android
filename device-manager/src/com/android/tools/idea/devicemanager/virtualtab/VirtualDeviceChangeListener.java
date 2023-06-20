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
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

final class VirtualDeviceChangeListener implements IDeviceChangeListener {
  private final @NotNull Supplier<Application> myGetApplication;
  private final @NotNull Runnable mySetAllOnline;

  @UiThread
  VirtualDeviceChangeListener(@NotNull VirtualDeviceTableModel model) {
    this(ApplicationManager::getApplication, model::setAllOnline);
  }

  @VisibleForTesting
  VirtualDeviceChangeListener(@NotNull Supplier<Application> getApplication, @NotNull Runnable setAllOnline) {
    myGetApplication = getApplication;
    mySetAllOnline = setAllOnline;
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
      case ONLINE:
        myGetApplication.get().invokeLater(mySetAllOnline);
        break;
      default:
        break;
    }
  }
}
