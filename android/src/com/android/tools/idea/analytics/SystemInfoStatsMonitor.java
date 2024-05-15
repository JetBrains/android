/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.analytics;

import com.android.repository.Revision;
import com.android.sdklib.internal.avd.EmulatorPackage;
import com.android.sdklib.internal.avd.EmulatorPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.base.Joiner;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.google.wireless.android.sdk.stats.EmulatorHost;
import com.google.wireless.android.sdk.stats.Hypervisor;
import com.google.wireless.android.sdk.stats.Hypervisor.HyperVState;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A generic system information monitor object.
 * The SystemInfoStatsMonitor class implements the monitoring and reporting
 * of the system-related attributes - CPU information, hypervisor, OS etc
 * It sends the data using the metric reporting API
 *
 */
public class SystemInfoStatsMonitor {

  private static final Logger LOG = Logger.getInstance(SystemInfoStatsMonitor.class);

  public enum CpuInfoFlags {
    ERROR(0),
    AMD(1 << 0),
    INTEL(1 << 1),
    OTHER_CPU(1 << 2),
    IN_VM(1 << 3),
    SUPPORTS_VIRT(1 << 4),
    UNKNOWN(1 << 30);

    private final int myValue;

    CpuInfoFlags(int value) {
      myValue = value;
    }

    public static EnumSet<CpuInfoFlags> fromExitCode(int code) {
      if (code == 0) {
        return EnumSet.of(ERROR);
      }

      EnumSet<CpuInfoFlags> result = EnumSet.noneOf(CpuInfoFlags.class);
      for (CpuInfoFlags flag : CpuInfoFlags.values()) {
        if (flag == ERROR) {
          continue;
        }
        if ((code & flag.myValue) != 0) {
          result.add(flag);
          code &= ~flag.myValue;
        }
      }

      if (code != 0) {
        // this isn't an error - some newer tools have new flags, and we don't know those yet
        LOG.warn(String.format("CpuInfoFlags.fromExitCode(): unknown flag values '0x%x'", code));
      }

      return result;
    }
  }

  // Upload and recalculate the state couple times a day, so we could have some
  // confidence that we drop at least one valid metric in 24 hours
  private static final int UPLOAD_INTERVAL_HOURS = 6;
  private static final Revision LOWEST_EMULATOR_REVISION_HYPERV = new Revision(25, 0, 3);
  private static final Revision LOWEST_EMULATOR_REVISION_CPU_INFO = new Revision(25, 1, 1);

  private static final int EMULATOR_CHECK_ERROR_EXIT_CODE = 100;

  private AndroidSdkHandler mySdkHandler = null;
  private ScheduledFuture<?> myUploadTask = null;

  private HyperVState myHyperVState = HyperVState.UNKNOWN_HYPERV_STATE;
  private EnumSet<CpuInfoFlags> myCpuInfo = EnumSet.noneOf(CpuInfoFlags.class);

  public void start() {
    if (!AnalyticsSettings.getOptedIn()) {
      return;
    }

    myUploadTask = JobScheduler.getScheduler().scheduleWithFixedDelay(
      new Runnable() {
        @Override
        public void run() {
          updateAndUploadStats();
        }
      },
      5, 60 * UPLOAD_INTERVAL_HOURS, TimeUnit.MINUTES);
  }

  private void updateAndUploadStats() {
    // double-check if the user has opted out of the metrics reporting since the
    // last run
    if (!AnalyticsSettings.getOptedIn()) {
      myUploadTask.cancel(true);
      return;
    }

    if (mySdkHandler == null) {
      mySdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    }

    if (SystemInfo.isWindows) {
      updateHyperVState();
    }
    updateCpuInfo();

    sendStats();
  }

  private void sendStats() {
    if (myHyperVState != HyperVState.UNKNOWN_HYPERV_STATE && myHyperVState != HyperVState.HYPERV_CHECK_ERROR) {
    }

    if (!myCpuInfo.contains(CpuInfoFlags.UNKNOWN) && !myCpuInfo.contains(CpuInfoFlags.ERROR)) {
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder()
                                   .setCategory(EventCategory.SYSTEM)
                                   .setKind(EventKind.HYPERVISOR)
                                   .setHypervisor(Hypervisor.newBuilder()
                                                  .setHyperVState(myHyperVState)));
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                                     .setCategory(EventCategory.SYSTEM)
                                     .setKind(EventKind.EMULATOR_HOST)
                                     .setEmulatorHost(EmulatorHost.newBuilder()
                                                      .setCpuManufacturer(Joiner.on(',').join(myCpuInfo))
                                     ));
  }


  private void updateHyperVState() {
    if (myHyperVState != HyperVState.UNKNOWN_HYPERV_STATE && myHyperVState != HyperVState.HYPERV_CHECK_ERROR) {
      // once calculated, it won't change until the OS restart
      return;
    }

    myHyperVState = calcHyperVState(mySdkHandler);
  }

  private void updateCpuInfo() {
    if (!myCpuInfo.isEmpty()
        && !myCpuInfo.contains(CpuInfoFlags.ERROR)
        && !myCpuInfo.contains(CpuInfoFlags.UNKNOWN)) {
      // once calculated, it won't change
      return;
    }

    myCpuInfo = calcCpuInfo(mySdkHandler);
  }

  @Nullable
  private static Integer runEmulatorCheck(@NotNull String argument,
                                          @NotNull Revision lowestEmulatorRevision,
                                          @NotNull AndroidSdkHandler handler) throws ExecutionException {
    EmulatorPackage emulatorPackage =
      EmulatorPackages.getEmulatorPackage(handler, new StudioLoggerProgressIndicator(SystemInfoStatsMonitor.class));
    if (emulatorPackage == null) {
      throw new ExecutionException("No SDK emulator package");
    }

    final Revision emulatorRevision = emulatorPackage.getVersion();
    if (emulatorRevision.compareTo(lowestEmulatorRevision) < 0) {
      return null;
    }

    Path checkBinary = emulatorPackage.getEmulatorCheckBinary();
    if (checkBinary == null) {
      throw new ExecutionException("No emulator-check binary in the SDK emulator package");
    }
    GeneralCommandLine commandLine = new GeneralCommandLine(checkBinary.toString(), argument);
    CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
    ProcessOutput output = process.runProcess();
    int exitCode = output.getExitCode();
    if (exitCode == EMULATOR_CHECK_ERROR_EXIT_CODE) {
      throw new ExecutionException(
        String.format("Emulator-check failed to check for '%s' with a generic error code %d",
                      argument, EMULATOR_CHECK_ERROR_EXIT_CODE));
    }

    return exitCode;
  }

  @NotNull
  private static HyperVState calcHyperVState(@NotNull AndroidSdkHandler handler) {
    try {
      Integer res = runEmulatorCheck("hyper-v", LOWEST_EMULATOR_REVISION_HYPERV, handler);
      if (res == null) {
        return HyperVState.UNKNOWN_HYPERV_STATE;
      }
      return exitCodeToHyperVState(res);
    } catch (ExecutionException e) {
      LOG.warn("Exception during Hyper-V state calculation", e);
      return HyperVState.HYPERV_CHECK_ERROR;
    }
  }

  private static HyperVState exitCodeToHyperVState(int exitCode) {
    switch (exitCode) {
      case   0: return HyperVState.HYPERV_ABSENT;
      case   1: return HyperVState.HYPERV_INSTALLED;
      case   2: return HyperVState.HYPERV_RUNNING;
      case 100: return HyperVState.HYPERV_CHECK_ERROR;
      default : return HyperVState.UNKNOWN_HYPERV_STATE;
    }
  }

  @NotNull
  private static EnumSet<CpuInfoFlags> calcCpuInfo(@NotNull AndroidSdkHandler handler) {
    try {
      Integer res = runEmulatorCheck("cpu-info", LOWEST_EMULATOR_REVISION_CPU_INFO, handler);
      if (res == null) {
        return EnumSet.of(CpuInfoFlags.UNKNOWN);
      }
      return CpuInfoFlags.fromExitCode(res);
    } catch (ExecutionException e) {
      LOG.warn("Exception during CPU information calculation", e);
      return EnumSet.of(CpuInfoFlags.ERROR);
    }
  }
}
