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
 * mA (milliamp).
 *
 * See also <a href="https://source.android.com/devices/tech/power/#profile-values">this article
 * on power profiles</a>, which this class aims to emulate.
 */
public interface PowerProfile {
  int getCpuUsage(@NotNull CpuCoreUsage[] usages);

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
    public int getCpuUsage(@NotNull CpuCoreUsage[] usages) {
      double totalMilliAmps = 0;
      for (CpuCoreUsage core : usages) {
        double f = core.myFrequencyKhz * 0.001; // normalize to MHz
        double f2 = f * f;
        double f3 = f2 * f;
        // Based on measurements on a Walleye device at various core frequencies for both the big and little cores.
        // The empirical results are then fitted against a cubic polynomial, resulting in the following equations.
        totalMilliAmps += (2.48408e-8 * f3 - 0.0000468129 * f2 + 0.0551123 * f - 1.96322) * core.myAppUsage * core.myCoreUsage;
        // TODO add small core equation below into the mix
        //small core: (5.79689e-9 * f3 - 8.31587e-6 * f2 + 0.0109841 * f + 0.513398) * core.myAppUsage;
      }
      return (int)totalMilliAmps;
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

  /**
   * Represents the per-core usage of the CPU.
   */
  final class CpuCoreUsage {
    public final double myAppUsage;
    public final double myCoreUsage;
    public final int myFrequencyKhz;

    /**
     * @param appUsage     The total amount of CPU resources of the target app, relative to actual CPU usage (not idle) [0, 1].
     * @param coreUsage    The amount of total usage of the core, relative to the total time elapsed [0, 1].
     * @param frequencyKhz The frequency (in kHz) the core was last operating at.
     */
    public CpuCoreUsage(double appUsage, double coreUsage, int frequencyKhz) {
      myAppUsage = appUsage;
      myCoreUsage = coreUsage;
      myFrequencyKhz = frequencyKhz;
    }
  }
}
