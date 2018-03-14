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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
     * The network is in a high power state and ready to send (radio only).
     */
    READY,

    /**
     * The network is actively sending data.
     */
    SENDING,

    /**
     * The network is actively receiving data.
     */
    RECEIVING,
  }

  /**
   * A default power profile based on energy values measured on a Pixel2.
   */
  final class DefaultPowerProfile implements PowerProfile {
    static final int MIN_CORE_FREQ_KHZ = 300000;
    static final int MAX_BIG_CORE_FREQ_KHZ = 2457600;
    static final int MAX_LITTLE_CORE_FREQ_KHZ = 1900800;

    @Override
    public int getCpuUsage(@NotNull CpuCoreUsage[] usages) {
      double totalMilliAmps = 0;
      if (usages.length == 0) {
        return 0;
      }

      List<CpuCoreUsage> usagesList = Arrays.asList(usages);
      Collections.sort(usagesList, Comparator.comparingInt(o -> o.myMaxFrequencyKhz));

      // TODO Support more than 2 CPU frequency bins?
      boolean higherFrequency; // We only support two frequency bins presently.
      for (CpuCoreUsage core : usagesList) {
        higherFrequency = core.myMaxFrequencyKhz > usagesList.get(0).myMaxFrequencyKhz;
        double fMhz = renormalizeFrequency(core, higherFrequency ? MAX_BIG_CORE_FREQ_KHZ : MAX_LITTLE_CORE_FREQ_KHZ);
        double f2 = fMhz * fMhz;
        double f3 = f2 * fMhz;
        // Based on measurements on a Walleye device at various core frequencies for both the big and little cores.
        // The empirical results are then fitted against a cubic polynomial, resulting in the following equations.
        if (higherFrequency) {
          totalMilliAmps += (2.48408e-8 * f3 - 0.0000468129 * f2 + 0.0551123 * fMhz - 1.96322) * core.myAppUsage * core.myCoreUsage;
        }
        else {
          totalMilliAmps += (5.79689e-9 * f3 - 8.31587e-6 * f2 + 0.0109841 * fMhz + 0.513398) * core.myAppUsage * core.myCoreUsage;
        }
      }
      return (int)totalMilliAmps;
    }

    @Override
    public int getNetworkUsage(@NotNull NetworkType type, @NotNull NetworkState state) {
      int usage = 0;
      if (type == NetworkType.WIFI) {
        switch (state) {
          case READY:
            break;
          case RECEIVING:
            usage += 100;
            break;
          case SENDING:
            usage += 250;
            break;
          case IDLE:
            usage += 1;
            break;
          default:
            break;
        }
      }
      else if (type == NetworkType.RADIO) {
        switch (state) {
          case READY:
            usage += 10;
            break;
          case RECEIVING:
            usage += 50;
            break;
          case SENDING:
            usage += 200;
            break;
          case IDLE:
            usage += 1;
            break;
          case SCANNING:
            usage += 5;
            break;
        }
      }
      return usage;
    }

    @VisibleForTesting
    static double renormalizeFrequency(@NotNull CpuCoreUsage core, int maxFrequencyKhz) {
      double clampedFreq = Ints.constrainToRange(core.myFrequencyKhz, core.myMinFrequencyKhz, core.myMaxFrequencyKhz);
      return (((clampedFreq - core.myMinFrequencyKhz) / (core.myMaxFrequencyKhz - core.myMinFrequencyKhz)) *
              (maxFrequencyKhz - MIN_CORE_FREQ_KHZ) + MIN_CORE_FREQ_KHZ) * 0.001; // Change to MHz as well.
    }
  }

  /**
   * Represents the per-core usage of the CPU.
   */
  final class CpuCoreUsage {
    public final double myAppUsage;
    public final double myCoreUsage;
    private final int myMinFrequencyKhz;
    private final int myMaxFrequencyKhz;
    public final int myFrequencyKhz;

    /**
     * @param appUsage        The total amount of CPU resources of the target app, relative to actual CPU usage (not idle) [0, 1].
     * @param coreUsage       The amount of total usage of the core, relative to the total time elapsed [0, 1].
     * @param minFrequencyKhz The min frequency (in kHz) the core is able to sustain.
     * @param maxFrequencyKhz The max frequency (in kHz) the core is able to lower to.
     * @param frequencyKhz    The frequency (in kHz) the core was last operating at.
     */
    public CpuCoreUsage(double appUsage, double coreUsage, int minFrequencyKhz, int maxFrequencyKhz, int frequencyKhz) {
      myAppUsage = appUsage;
      myCoreUsage = coreUsage;
      myMinFrequencyKhz = minFrequencyKhz;
      myMaxFrequencyKhz = maxFrequencyKhz;
      myFrequencyKhz = frequencyKhz;
    }
  }
}
