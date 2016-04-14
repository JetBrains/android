/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.profilerclient;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Global singleton service provider to obtain access to Studio Profiler-enabled devices and apps.
 */
public class ProfilerService {
  private static final Logger LOG = Logger.getInstance(ProfilerService.class.getCanonicalName());

  private static ProfilerService INSTANCE;

  @NotNull private final Map<IDevice, DeviceClientChannel> myConnectedDevices = new HashMap<IDevice, DeviceClientChannel>();

  public static void setInstance(@NotNull ProfilerService instance) {
    INSTANCE = instance;
  }

  public static ProfilerService getInstance() {
    return INSTANCE;
  }

  /**
   * Creates a connection to a target device.
   *
   * @return an abstraction of a connection to a device, or null if a connection can not be established.
   */
  @Nullable
  public synchronized DeviceClient connect(@NotNull IDevice device, @NotNull ProfilerClientListener profilerListener) {
    if (device.getState() != IDevice.DeviceState.ONLINE) {
      return null;
    }

    DeviceClientChannel connection;
    if (!myConnectedDevices.containsKey(device)) {
      connection = DeviceClientChannel.connect(device, profilerListener);
      if (connection == null) {
        return null;
      }
      myConnectedDevices.put(device, connection);
    }
    else {
      connection = myConnectedDevices.get(device);
      connection.addListener(profilerListener);
    }

    return new DeviceClient(connection, profilerListener);
  }

  /**
   * Disconnects cleanly from the target device.
   *
   * @param appClient is the desired target as returned from {@link ProfilerService#connect(IDevice, ProfilerClientListener)}
   */
  public synchronized void disconnect(@NotNull DeviceClient deviceClient) {
    if (!myConnectedDevices.containsKey(deviceClient.getDeviceClientChannel().getDevice())) {
      LOG.error("Caller attempted to disconnected from a device that was not connected.");
    }

    deviceClient.getDeviceClientChannel().removeListener(deviceClient.getProfilerClientListener());

    if (deviceClient.getDeviceClientChannel().getProfilerClientListenersCount() == 0) {
      myConnectedDevices.remove(deviceClient.getDeviceClientChannel().getDevice());
    }
  }
}
