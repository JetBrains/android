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
package com.android.tools.idea.run.deployable;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplicationIdResolver implements IDebugBridgeChangeListener, IDeviceChangeListener {
  @NotNull private final Map<IDevice, Device> myDevices = new ConcurrentHashMap<>();
  @NotNull private final ThreadPoolExecutor myApplicationIdResolverExecutor;

  public ApplicationIdResolver() {
    myApplicationIdResolverExecutor = new ThreadPoolExecutor(
      4, // 4 permanent threads since we allow them to time out.
      4, // We'll handle at max 4 threads/Clients at the same time.
      1, // Let the threads live for max 1 second.
      TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(),
      new ThreadFactoryBuilder().setNameFormat("package-name-resolver-%d").build());
    myApplicationIdResolverExecutor.allowCoreThreadTimeOut(true);

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
  }

  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);

    myApplicationIdResolverExecutor.shutdownNow();
    try {
      if (!myApplicationIdResolverExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        Logger.getInstance(ApplicationIdResolver.class).warn("ApplicationIdResolver failed to shut down its executor within the time limit.");
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }


  @NotNull
  public List<Client> resolve(@NotNull IDevice iDevice, @NotNull String applicationId) {
    Device device = myDevices.get(iDevice);
    return device == null ? Collections.emptyList() : device.findClientWithApplicationId(applicationId);
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    if (bridge != null && bridge.isConnected() && bridge.hasInitialDeviceList()) {
      for (IDevice iDevice : bridge.getDevices()) {
        refresh(iDevice);
      }
    }
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    refresh(device);
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    myDevices.remove(device);
  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {
    if ((changeMask & (IDevice.CHANGE_STATE | IDevice.CHANGE_CLIENT_LIST)) != 0) {
      refresh(device);
    }
  }

  private void refresh(@NotNull IDevice iDevice) {
    myDevices.compute(iDevice, (ignored, device) -> {
      // Note isOffline() means if device is specifically in offline mode, not including booting mode.
      // Therefore we don't use it here.
      if (!iDevice.isOnline()) {
        return null;
      }

      Device newDevice = device;
      if (newDevice == null) {
        newDevice = new Device(myApplicationIdResolverExecutor, iDevice);
      }
      newDevice.refresh();
      return newDevice;
    });
  }
}
