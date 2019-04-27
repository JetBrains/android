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

import com.android.tools.datastore.LogService;
import com.android.tools.datastore.energy.PowerProfile.CpuCoreUsage;
import com.android.tools.datastore.poller.EnergyDataPoller;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuCoreUsageData;
import com.android.tools.profiler.proto.Cpu.CpuUsageData;
import com.google.common.primitives.Doubles;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class CpuConfig {
  @NotNull private final LogService myLogService;
  private final int[] myCpuCoreMinFreqInKhz;
  private final int[] myCpuCoreMaxFreqInKhz;
  private final int myBigCoreMaxFrequency;
  private final boolean myIsMinMaxCoreFreqValid;

  public CpuConfig(Cpu.CpuCoreConfigData cpuCoreConfigData, @NotNull LogService logService) {
    int coreConfigCount = cpuCoreConfigData.getCoreConfigsCount();
    myCpuCoreMinFreqInKhz = new int[coreConfigCount];
    myCpuCoreMaxFreqInKhz = new int[coreConfigCount];
    myLogService = logService;

    // Core ID should always be in the range of [0..num_cores-1] and unique.
    boolean[] myIsCpuCorePopulated = new boolean[coreConfigCount];
    boolean isValidCpuCoreConfig = coreConfigCount > 0;

    if (isValidCpuCoreConfig) {
      for (Cpu.CpuCoreConfig config : cpuCoreConfigData.getCoreConfigsList()) {
        int core = config.getCore();
        if (core >= coreConfigCount || myIsCpuCorePopulated[core]) {
          getLogger().debug(core > coreConfigCount ?
                            String.format("Core index %d is >= the number of configs (%d) reported.", core, coreConfigCount) :
                            "Core index already populated.");
          isValidCpuCoreConfig = false;
          break;
        }

        myIsCpuCorePopulated[core] = true;
        int minFreq = config.getMinFrequencyInKhz();
        int maxFreq = config.getMaxFrequencyInKhz();
        if (minFreq <= 0 || minFreq >= maxFreq) {
          getLogger().debug(minFreq <= 0 ?
                            String.format("Min frequency %d <= 0.", minFreq) :
                            String.format("Min frequency %d >= max frequency of %d.", minFreq, maxFreq));
          isValidCpuCoreConfig = false;
          break;
        }
        myCpuCoreMinFreqInKhz[core] = minFreq;
        myCpuCoreMaxFreqInKhz[core] = maxFreq;
      }

      myIsMinMaxCoreFreqValid = isValidCpuCoreConfig;
      if (myIsMinMaxCoreFreqValid) {
        int maxFrequency = 0;
        for (int coreMax : myCpuCoreMaxFreqInKhz) {
          if (coreMax > maxFrequency) {
            maxFrequency = coreMax;
          }
        }
        myBigCoreMaxFrequency = maxFrequency;
      }
      else {
        myBigCoreMaxFrequency = 0;
      }
    }
    else {
      getLogger().debug("No valid configs found!");
      myIsMinMaxCoreFreqValid = false;
      myBigCoreMaxFrequency = 0;
    }
  }

  /**
   * Calculates the per-core CPU usage data given two {@link CpuUsageData} samples.
   *
   * @param prevUsageData
   * @param currUsageData
   * @return an array of {@link CpuCoreUsage} structures. Note that individual elements may have been reordered from the input samples.
   */
  @NotNull
  public CpuCoreUsage[] getCpuCoreUsages(@NotNull CpuUsageData prevUsageData, CpuUsageData currUsageData) {
    // We'll assume that CpuUsageData is more reliable than CpuCoreConfigResponse.
    final int coreCount = currUsageData.getCoresCount();
    if (coreCount != prevUsageData.getCoresCount()) {
      getLogger().info(String.format("CPU config changing from %d to %d cores.", prevUsageData.getCoresCount(), coreCount));
      return new CpuCoreUsage[0];
    }
    CpuCoreUsage[] cpuCoresUtilization = new CpuCoreUsage[coreCount];

    if (myIsMinMaxCoreFreqValid) {
      // Calculate app-system ratio, so we can just multiply by each core's system-elapsed ratio to get the estimated app-elapsed ratio.
      double system = currUsageData.getSystemCpuTimeInMillisec() - prevUsageData.getSystemCpuTimeInMillisec();
      double appSystemRatio = (currUsageData.getAppCpuTimeInMillisec() - prevUsageData.getAppCpuTimeInMillisec()) / system;

      List<CpuCoreUsageData> coresUsageData = currUsageData.getCoresList();
      List<CpuCoreUsageData> prevCoresUsageData = prevUsageData.getCoresList();

      for (int i = 0; i < coreCount; i++) {
        CpuCoreUsageData currCore = coresUsageData.get(i);
        CpuCoreUsageData prevCore = prevCoresUsageData.get(i);
        assert i < myCpuCoreMinFreqInKhz.length;
        int minFreqKhz = myCpuCoreMinFreqInKhz[i];
        int maxFreqKhz = myCpuCoreMaxFreqInKhz[i];
        double elapsedCore = currCore.getElapsedTimeInMillisec() - prevCore.getElapsedTimeInMillisec();
        double corePercent = (currCore.getSystemCpuTimeInMillisec() - prevCore.getSystemCpuTimeInMillisec()) / elapsedCore;
        cpuCoresUtilization[i] =
          new CpuCoreUsage(currCore.getCore(), appSystemRatio, corePercent, minFreqKhz, maxFreqKhz, currCore.getFrequencyInKhz(),
                           currCore.getFrequencyInKhz() != myBigCoreMaxFrequency);
      }
    }
    else {
      // Fallback for parsing failure -- calculate app-elapsed ratio directly.
      double elapsed = currUsageData.getElapsedTimeInMillisec() - prevUsageData.getElapsedTimeInMillisec();
      double appElapsedRatio = (currUsageData.getAppCpuTimeInMillisec() - prevUsageData.getAppCpuTimeInMillisec()) / elapsed;
      // appPercent is [0, 1.0] for any number of cores. So as a trick, we can just multiply by coreCount to get shares that we can assign
      // to each core.
      double normalizedCoreShares = appElapsedRatio * coreCount;

      for (int i = 0; i < coreCount; i++) {
        // For fallback, instead of evenly distributing core usage, we'll force them onto as little number of cores as possible.
        // The reason is that most apps tend to be poorly threaded.
        cpuCoresUtilization[i] = new CpuCoreUsage(i, Doubles.constrainToRange(normalizedCoreShares, 0.0, 1.0), 1.0, 0, 1, 1, false);
        normalizedCoreShares -= 1.0;
      }
    }

    return cpuCoresUtilization;
  }

  /**
   * The default core config has 0 cores. Assume that if a device reports 0 cores, then it has an invalid core config.
   *
   * @return true if the initialized config is valid, false otherwise.
   */
  public boolean getIsMinMaxCoreFreqValid() {
    return myIsMinMaxCoreFreqValid;
  }

  @NotNull
  private LogService.Logger getLogger() {
    return myLogService.getLogger(EnergyDataPoller.class);
  }
}
