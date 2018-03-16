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
import com.google.common.primitives.Longs;
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

  int getNetworkUsage(@NotNull NetworkStats networkStats);

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
     * The network is actively receiving data.
     */
    RECEIVING,

    /**
     * The network is actively sending data.
     */
    SENDING,
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
      if (usages.length == 0) {
        return 0;
      }

      double totalMilliAmps = 7.201; // Base power consumption for a suspended + idle CPU.
      List<CpuCoreUsage> usagesList = Arrays.asList(usages);
      Collections.sort(usagesList, Comparator.comparingInt(o -> o.myMaxFrequencyKhz));

      // TODO(b/75968214): Support more than 2 CPU frequency bins?
      // We only support up to two frequency bins presently.
      boolean lowerFrequencyCore; // Defaults single core system to big core.
      boolean lowerClusterActivationAdded = false;
      boolean higherClusterActivationAdded = false;
      boolean cpuActivationAdded = false;
      for (CpuCoreUsage core : usagesList) {
        lowerFrequencyCore = core.myMaxFrequencyKhz < usagesList.get(usagesList.size() - 1).myMaxFrequencyKhz;
        double fMhz = renormalizeFrequency(core, lowerFrequencyCore ? MAX_LITTLE_CORE_FREQ_KHZ : MAX_BIG_CORE_FREQ_KHZ);
        double f2 = fMhz * fMhz;
        double f3 = f2 * fMhz;

        // Approximately add CPU active cost and cluster active costs (approximately 6mA per category).
        if (fMhz > MIN_CORE_FREQ_KHZ) {
          if (!cpuActivationAdded) {
            cpuActivationAdded = true;
            totalMilliAmps += 17.757;
          }

          if (lowerFrequencyCore && !lowerClusterActivationAdded) {
            lowerClusterActivationAdded = true;
            totalMilliAmps += 6.478;
          }
          else if (!lowerFrequencyCore && !higherClusterActivationAdded) {
            higherClusterActivationAdded = true;
            totalMilliAmps += 6.141;
          }
        }

        // Add per-cost cost.
        // Based on measurements on a Walleye device at various core frequencies for both the big and little cores.
        // The empirical results are then fitted against a cubic polynomial, resulting in the following equations.
        if (lowerFrequencyCore) {
          totalMilliAmps += (5.79689e-9 * f3 - 8.31587e-6 * f2 + 0.0109841 * fMhz + 0.513398) * core.myAppUsage * core.myCoreUsage;
        }
        else {
          totalMilliAmps += (2.48408e-8 * f3 - 0.0000468129 * f2 + 0.0551123 * fMhz - 1.96322) * core.myAppUsage * core.myCoreUsage;
        }
      }
      return (int)totalMilliAmps;
    }

    @Override
    public int getNetworkUsage(@NotNull NetworkStats networkStats) {
      if (networkStats.myNetworkType != NetworkType.WIFI && networkStats.myNetworkType != NetworkType.RADIO) {
        return 0;
      }

      if (networkStats.myReceivingBps == 0 && networkStats.mySendingBps == 0) {
        return 1;
      }

      int usage = 0;
      // Since we don't have too much information other than bps, we will assume a power model consisting of:
      // power_use = network_baseline + scaling_factor * load
      // where load is a rough eyeball of setup/teardown costs, TCP overhead, packet head overhead, etc....
      // Note: radio READY state is 10mA and SCANNING is 5mA.
      if (networkStats.myReceivingBps > 0) {
        usage += networkStats.myNetworkType == NetworkType.WIFI
                 ? fitBps(1, 99, networkStats.myReceivingBps, 1000000)
                 : fitBps(10, 40, networkStats.myReceivingBps, 200000);
      }
      if (networkStats.mySendingBps > 0) {
        usage += networkStats.myNetworkType == NetworkType.WIFI
                 ? fitBps(1, 249, networkStats.mySendingBps,  500000)
                 : fitBps(10, 190, networkStats.mySendingBps, 200000);
      }
      return usage;
    }

    @VisibleForTesting
    static double renormalizeFrequency(@NotNull CpuCoreUsage core, int maxFrequencyKhz) {
      double clampedFreq = Ints.constrainToRange(core.myFrequencyKhz, core.myMinFrequencyKhz, core.myMaxFrequencyKhz);
      return (((clampedFreq - core.myMinFrequencyKhz) / (core.myMaxFrequencyKhz - core.myMinFrequencyKhz)) *
              (maxFrequencyKhz - MIN_CORE_FREQ_KHZ) + MIN_CORE_FREQ_KHZ) * 0.001; // Change to MHz as well.
    }

    /**
     * Fits a BPS value to the specified piece-wise L0 continuous linear function to produce a mA figure.
     *
     * @param base       Fixed constant mA for base usage.
     * @param scale      Variable mA for network activity.
     * @param bps        Actual BPS of network activity.
     * @param normalizer This value is effectively the max value bps can be for the sake of computing the current draw. Anything higher
     *                   than the normalizer will be capped at the value of the normalizer. This effectively caps and maps bps to the
     *                   [0.0, 1.0] range.
     * @return the mapped current draw (mA) for the given network speeds.
     */
    static int fitBps(double base, double scale, long bps, long normalizer) {
      return (int)(base + scale * (double)Longs.constrainToRange(bps, 0, normalizer) / (double)normalizer);
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

  final class NetworkStats {
    public final NetworkType myNetworkType;
    public final long myReceivingBps;
    public final long mySendingBps;

    public NetworkStats(NetworkType type, long receivingBps, long sendingBps) {
      myNetworkType = type;
      myReceivingBps = receivingBps;
      mySendingBps = sendingBps;
    }
  }
}
