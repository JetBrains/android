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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Global singleton service provider to obtain access to Studio Profiler-enabled devices and apps.
 */
public class ProfilerService {
  // TODO move this to a place where it can be shared between the native build system and Studio.
  private static final String EXPECTED_VERSION = "15.1.876456";

  private static final Logger LOG = Logger.getInstance(ProfilerService.class.getCanonicalName());

  @NotNull private final Map<IDevice, DeviceProfilerService> myDeviceClientServices = new HashMap<>();

  @NotNull
  public static ProfilerService getInstance() {
    return ServiceManager.getService(ProfilerService.class);
  }

  /**
   * Creates a connection to a target device.
   *
   * @param userKey any non-null key that will later be needed for {@link #disconnect(Disposable, DeviceProfilerService)}
   * @return an abstraction of a connection to a device, or null if a connection can not be established.
   */
  @Nullable
  public synchronized DeviceProfilerService connect(@NotNull Object userKey, @NotNull IDevice device) {
    if (device.getState() != IDevice.DeviceState.ONLINE) {
      return null;
    }

    DeviceProfilerService deviceProfilerService;
    if (myDeviceClientServices.containsKey(device)) {
      deviceProfilerService = myDeviceClientServices.get(device);
      deviceProfilerService.register(userKey);
      return deviceProfilerService;
    }

    deviceProfilerService = DeviceProfilerService.createService(device);
    if (deviceProfilerService == null) {
      return null;
    }
    myDeviceClientServices.put(device, deviceProfilerService);
    deviceProfilerService.register(userKey);

    String versionResponse;
    try {
      // TODO Change getVersion be more asynchronous.
      versionResponse = deviceProfilerService.getDeviceService().getVersion(
        com.android.tools.profiler.proto.ProfilerService.VersionRequest.getDefaultInstance()).getVersion();
    }
    catch (RuntimeException e) {
      LOG.info("Error connecting to profiler server: " + e.toString());
      disconnect(userKey, deviceProfilerService);
      return null;
    }

    if (!versionResponse.equals(EXPECTED_VERSION)) {
      LOG.info("Device '" +
               device.getAvdName() +
               "' did not return the expected version. Expected: " +
               EXPECTED_VERSION +
               ", received: " +
               versionResponse +
               ".");
      disconnect(userKey, deviceProfilerService);
      return null;
    }

    return deviceProfilerService;
  }

  /**
   * Disconnects from the target device.
   *
   * @param userKey the non-null key that was used in the {@link #connect(Object, IDevice)} call
   * @param deviceProfilerService the return value of the {@link #connect(Object, IDevice)} call
   */
  public void disconnect(@NotNull Object userKey, @NotNull DeviceProfilerService deviceProfilerService) {
    if (deviceProfilerService.unregister(userKey)) {
      myDeviceClientServices.remove(deviceProfilerService.getDevice());
    }
  }

  synchronized void stop(@NotNull DeviceProfilerService deviceProfilerService) {
    myDeviceClientServices.remove(deviceProfilerService.getDevice());
  }
}
