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
package com.android.tools.idea.run.deployable;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * A thread-safe utility class to asynchronously retrieve the version of an {@link IDevice}.
 * This class avoids {@link IDevice#getVersion()}'s synchronous property fetch.
 */
public class DeviceVersion implements IDebugBridgeChangeListener, IDeviceChangeListener {
  @NotNull
  private final Map<IDevice, ListenableFuture<AndroidVersion>> myVersions;

  public DeviceVersion() {
    myVersions = new ConcurrentHashMap<>();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
  }

  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  @NotNull
  public ListenableFuture<AndroidVersion> get(@NotNull IDevice iDevice) {
    return myVersions.compute(
      iDevice,
      (d, f) -> {
        if (f != null && f.isDone()) {
          try {
            AndroidVersion version = f.get();
            if (version != AndroidVersion.DEFAULT) {
              return f;
            }
          }
          catch (Exception ignored) {
          }
        }

        // Otherwise, we fall through (since there's been an error, including cancelling the Future) and try again.
        return Futures.submit(() -> getVersion(d), AppExecutorUtil.getAppExecutorService());
      });
  }

  private static @NotNull AndroidVersion getVersion(@NotNull IDevice device) {
    try {
      var level = device.getProperty(IDevice.PROP_BUILD_API_LEVEL);

      if (level == null) {
        return AndroidVersion.DEFAULT;
      }

      return new AndroidVersion(Integer.parseInt(level), device.getProperty(IDevice.PROP_BUILD_CODENAME));
    }
    catch (Exception exception) {
      return AndroidVersion.DEFAULT;
    }
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    if (bridge != null && bridge.isConnected() && bridge.hasInitialDeviceList()) {
      myVersions.keySet().retainAll(Arrays.asList(bridge.getDevices()));
    }
    else {
      myVersions.clear();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    // Do nothing -- we won't prefetch the version info for now.
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    myVersions.remove(device);
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    // Do nothing.
  }
}
