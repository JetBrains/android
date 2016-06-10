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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class DevicePortForwarder {
  private static final Logger LOG = Logger.getInstance(DevicePortForwarder.class.getCanonicalName());

  private static final int LOCAL_PROFILER_CLIENT_PORT_RANGE_START = 46623;
  private static final int MAX_PORT = 65536;

  /**
   * Creates a port forward to the target device's profiler server.
   *
   * @return the port that can be used to forward data to the device
   */
  static synchronized int forward(@NotNull IDevice device, @NotNull String unixDomainName) {
    return forward(device, unixDomainName, -1);
  }

  static synchronized int forward(@NotNull IDevice device, int devicePort) {
    return forward(device, null, devicePort);
  }

  private static synchronized int forward(@NotNull IDevice device, @Nullable String unixDomainName, int devicePort) {
    int hostPort = LOCAL_PROFILER_CLIENT_PORT_RANGE_START;
    boolean isDeviceOnline = device.isOnline();

    for (; hostPort < MAX_PORT && isDeviceOnline; ++hostPort) {
      try {
        if (unixDomainName != null) {
          assert devicePort < 0;
          device.createForward(hostPort, unixDomainName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        }
        else {
          assert devicePort >= 0 && devicePort <= MAX_PORT;
          device.createForward(hostPort, devicePort);
        }
        break;
      }
      catch (TimeoutException e) {
        LOG.error("Timed out while attempting to create port forward", e);
        return -1;
      }
      catch (AdbCommandRejectedException e) {
        LOG.error("ADB command error while attempting to create port forward", e);
      }
      catch (IOException e) {
        LOG.error("IO error while attempting to create port forward", e);
      }
      finally {
        isDeviceOnline &= device.isOnline();
      }
    }

    if (!isDeviceOnline) {
      LOG.error("Could not establish port forward. Device is offline.");
      return -1;
    }

    if (hostPort == MAX_PORT) {
      LOG.error("Could not establish port forward. All ports in use in valid range.");
      return -1;
    }

    return hostPort;
  }

  static synchronized void removeForward(@NotNull IDevice device, int hostPort, @NotNull String unixDomainName) {
    try {
      device.removeForward(hostPort, unixDomainName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
    }
    catch (AdbCommandRejectedException | TimeoutException | IOException ignored) {}
  }

  static synchronized void removeForward(@NotNull IDevice device, int hostPort, int devicePort) {
    try {
      device.removeForward(hostPort, devicePort);
    }
    catch (AdbCommandRejectedException | TimeoutException | IOException ignored) {}
  }
}
