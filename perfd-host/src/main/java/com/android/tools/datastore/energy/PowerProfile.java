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
package com.android.tools.datastore.energy;

import com.android.tools.profiler.proto.NetworkProfiler;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the current used for performing various operations. All methods return values in
 * mAh (milliamp / hour).
 *
 * See also <a href="https://source.android.com/devices/tech/power/#profile-values">this article
 * on power profiles</a>, which this class aims to emulate.
 */
public interface PowerProfile {
  int getCpuUsage(double percent);

  int getNetworkUsage(@NotNull NetworkType type, @NotNull NetworkState state);

  enum NetworkType {
    WIFI,
    RADIO,
    NONE;

    @NotNull
    public static NetworkType from(NetworkProfiler.ConnectivityData.NetworkType protoNetworkType) {
      switch (protoNetworkType) {
        case MOBILE:
          return RADIO;
        case WIFI:
          return WIFI;
        default:
          return NONE;
      }
    }
  }

  enum NetworkState {
    /**
     * The network is in a low power state
     */
    IDLE,

    /**
     * The network is currently looking for endpoints to connect to.
     */
    SCANNING,

    /**
     * The network is in a high power state and ready to send (radio only)
     */
    READY,

    /**
     * The network is actively sending and/or receiving data
     */
    ACTIVE,
  }

  /**
   * A default power profile based on energy values measured on a Pixel2.
   */
  final class DefaultPowerProfile implements PowerProfile {
    @Override
    public int getCpuUsage(double percent) {
      return (int)(percent * 500);
    }

    @Override
    public int getNetworkUsage(@NotNull NetworkType type, @NotNull NetworkState state) {
      int usage = 0;
      if (type == NetworkType.WIFI) {
        if (state == NetworkState.ACTIVE) {
          usage += 150;
        }
      }
      else if (type == NetworkType.RADIO) {
        if (state == NetworkState.ACTIVE) {
          usage += 200;
        }
        else if (state == NetworkState.READY) {
          usage += 100;
        }
      }
      return usage;
    }
  }
}
