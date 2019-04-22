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

import com.android.tools.profiler.proto.Network;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the current used for performing various operations. All methods return values in
 * mA (milliamp).
 * <p>
 * See also <a href="https://source.android.com/devices/tech/power/#profile-values">this article
 * on power profiles</a>, which this class aims to emulate.
 */
public interface PowerProfile {
  String GPS_PROVIDER = "gps";
  String NETWORK_PROVIDER = "network";
  String PASSIVE_PROVIDER = "passive";

  int getCpuUsage(@NotNull CpuCoreUsage[] usages);

  int getNetworkUsage(@NotNull NetworkStats networkStats);

  int getLocationUsage(@NotNull LocationStats locationStats);

  enum NetworkType {
    WIFI,
    RADIO,
    NONE;

    @NotNull
    public static NetworkType from(Network.NetworkTypeData.NetworkType protoNetworkType) {
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

  enum LocationType {
    NONE,
    PASSIVE,
    NETWORK,
    GPS_ACQUIRE,
    GPS;

    @NotNull
    public static LocationType from(@NotNull String protoLocationProvider) {
      switch (protoLocationProvider) {
        case GPS_PROVIDER:
          return GPS;
        case NETWORK_PROVIDER:
          return NETWORK;
        case PASSIVE_PROVIDER:
          return PASSIVE;
        default:
          return NONE;
      }
    }
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

      double totalMilliAmps = 0.0;
      List<CpuCoreUsage> usagesList = Arrays.asList(usages);
      Collections.sort(usagesList, Comparator.comparingInt(o -> o.myMaxFrequencyKhz));

      // TODO(b/75968214): Support more than 2 CPU frequency bins?
      // We only support up to two frequency bins presently.
      boolean lowerClusterActivationAdded = false;
      boolean higherClusterActivationAdded = false;
      boolean cpuActivationAdded = false;
      for (CpuCoreUsage core : usagesList) {
        double fMhz = renormalizeFrequency(core, core.myIsLittleCore ? MAX_LITTLE_CORE_FREQ_KHZ : MAX_BIG_CORE_FREQ_KHZ);
        double f2 = fMhz * fMhz;
        double f3 = f2 * fMhz;

        // Approximately add CPU active cost and cluster active costs (approximately 6mA per category).
        if (core.myAppUsage > 0.0) {
          if (!cpuActivationAdded) {
            cpuActivationAdded = true;
            totalMilliAmps += 17.757;
          }

          if (core.myIsLittleCore && !lowerClusterActivationAdded) {
            lowerClusterActivationAdded = true;
            totalMilliAmps += 6.478;
          }
          else if (!core.myIsLittleCore && !higherClusterActivationAdded) {
            higherClusterActivationAdded = true;
            totalMilliAmps += 6.141;
          }
        }

        // Add per-cost cost.
        // Based on measurements on a Walleye device at various core frequencies for both the big and little cores.
        // The empirical results are then fitted against a cubic polynomial, resulting in the following equations.
        if (core.myIsLittleCore) {
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
        return 0;
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
                 ? fitBps(1, 249, networkStats.mySendingBps, 500000)
                 : fitBps(10, 190, networkStats.mySendingBps, 200000);
      }
      return usage;
    }

    @Override
    public int getLocationUsage(@NotNull LocationStats locationStats) {
      // TODO(b/77588320): Change this to actually charge for the duration the GPS is on.
      // For initial version, we'll reshape (90mA * duration) into a single spike over the sample period.
      switch (locationStats.myLocationType) {
        case GPS:
          return (int)(90 * (double)locationStats.myDurationNs / (double)locationStats.mySampleIntervalNs);
        case GPS_ACQUIRE:
          return 90;
        case NETWORK:
          return (int)(30 * (double)locationStats.myDurationNs / (double)locationStats.mySampleIntervalNs);
        case PASSIVE:
          // fall through
        default:
          return 0;
      }
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
    public final int myCoreId;
    public final int myMinFrequencyKhz;
    public final int myMaxFrequencyKhz;
    public final int myFrequencyKhz;
    public final boolean myIsLittleCore;

    /**
     * @param coreId          The index/ID of the core.
     * @param appUsage        The total amount of CPU resources of the target app, relative to actual CPU usage (not idle) [0, 1].
     * @param coreUsage       The amount of total usage of the core, relative to the total time elapsed [0, 1].
     * @param minFrequencyKhz The min frequency (in kHz) the core is able to sustain.
     * @param maxFrequencyKhz The max frequency (in kHz) the core is able to lower to.
     * @param frequencyKhz    The frequency (in kHz) the core was last operating at.
     * @param isLittleCore    Whether or not this is a small core (for ARM big.LITTLE CPU configurations).
     */
    public CpuCoreUsage(int coreId,
                        double appUsage,
                        double coreUsage,
                        int minFrequencyKhz,
                        int maxFrequencyKhz,
                        int frequencyKhz,
                        boolean isLittleCore) {
      myCoreId = coreId;
      myAppUsage = appUsage;
      myCoreUsage = coreUsage;
      myMinFrequencyKhz = minFrequencyKhz;
      myMaxFrequencyKhz = maxFrequencyKhz;
      myFrequencyKhz = frequencyKhz;
      myIsLittleCore = isLittleCore;
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

  final class LocationEvent {
    public final int myEventId;
    public final LocationType myLocationType;

    public LocationEvent(int id, @NotNull LocationType type) {
      myEventId = id;
      myLocationType = type;
    }
  }

  final class LocationStats {
    public final LocationType myLocationType;
    public final long myDurationNs;
    public final long mySampleIntervalNs;

    public LocationStats(@NotNull LocationType type, long durationNs, long sampleIntervalNs) {
      myLocationType = type;
      myDurationNs = durationNs;
      mySampleIntervalNs = sampleIntervalNs;
    }
  }
}
