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
package com.android.tools.idea.sdk;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.startup.AndroidSdkInitializer;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Joiner;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A generic system information monitor object.
 * The SystemInfoStatsMonitor class implements the monitoring and reporting
 * of the system-related attributes - CPU information, hypervisor, OS etc
 * It sends the data using the metric reporting API
 *
 */
public class SystemInfoStatsMonitor {

  private static final Logger LOG = Logger.getInstance(SystemInfoStatsMonitor.class);

  public enum HyperVState {
    ABSENT,     // No hyper-V found
    INSTALLED,  // Hyper-V is installed but not running
    RUNNING,    // Hyper-V is up and running
    ERROR,      // Failed to detect status
    UNKNOWN;    // Some error happened in Android Studio

    public static HyperVState fromExitCode(int code) {
      switch (code) {
        case   0: return ABSENT;
        case   1: return INSTALLED;
        case   2: return RUNNING;
        case 100: return ERROR;
        default : return UNKNOWN;
      }
    }
  }

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
  private static final Revision LOWEST_TOOLS_REVISION_HYPERV = new Revision(25, 0, 3);
  private static final Revision LOWEST_TOOLS_REVISION_CPU_INFO = new Revision(25, 1, 1);

  private static final int EMULATOR_CHECK_ERROR_EXIT_CODE = 100;

  private AndroidSdkHandler mySdkHandler = null;
  private ScheduledFuture<?> myUploadTask = null;

  private HyperVState myHyperVState = HyperVState.UNKNOWN;
  private EnumSet<CpuInfoFlags> myCpuInfo = EnumSet.noneOf(CpuInfoFlags.class);

  public void start() {
    if (!UsageTracker.getInstance().canTrack()) {
      return;
    }

    myUploadTask = JobScheduler.getScheduler().scheduleAtFixedRate(
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
    if (!UsageTracker.getInstance().canTrack()) {
      myUploadTask.cancel(true);
      return;
    }

    if (mySdkHandler == null) {
      mySdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    }

    if (SystemInfo.isWindows) {
      updateHyperVState();
    }
    updateCpuInfo();

    sendStats();
  }

  private void sendStats() {
    String hyperVString = null;
    if (myHyperVState != HyperVState.UNKNOWN && myHyperVState != HyperVState.ERROR) {
      hyperVString = myHyperVState.toString();
    }

    String cpuInfoString = null;
    if (!myCpuInfo.contains(CpuInfoFlags.UNKNOWN) && !myCpuInfo.contains(CpuInfoFlags.ERROR)) {
      cpuInfoString = Joiner.on(',').join(myCpuInfo);
    }

    UsageTracker.getInstance().trackSystemInfo(hyperVString, cpuInfoString);
  }


  private void updateHyperVState() {
    if (myHyperVState != HyperVState.UNKNOWN && myHyperVState != HyperVState.ERROR) {
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

  @NotNull
  private static File getEmulatorCheckBinary(@NotNull AndroidSdkHandler handler) {
    return new File(handler.getLocation(), FileUtil.join(SdkConstants.OS_SDK_TOOLS_FOLDER, SdkConstants.FN_EMULATOR_CHECK));
  }

  @Nullable
  private static Integer runEmulatorCheck(@NotNull String argument,
                                          @NotNull Revision lowestToolsRevisiion,
                                          @NotNull AndroidSdkHandler handler) throws ExecutionException {
    LocalPackage toolsPackage = handler.getLocalPackage(SdkConstants.FD_TOOLS,
                                                        new StudioLoggerProgressIndicator(AndroidSdkInitializer.class));
    if (toolsPackage == null) {
      throw new ExecutionException("No SDK tools package");
    }

    final Revision toolsRevision = toolsPackage.getVersion();
    if (toolsRevision.compareTo(lowestToolsRevisiion) < 0) {
      return null;
    }

    File checkBinary = getEmulatorCheckBinary(handler);
    if (!checkBinary.isFile()) {
      throw new ExecutionException("No emulator-check binary in the SDK tools package");
    }
    GeneralCommandLine commandLine = new GeneralCommandLine(checkBinary.getPath(), argument);
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
      Integer res = runEmulatorCheck("hyper-v", LOWEST_TOOLS_REVISION_HYPERV, handler);
      if (res == null) {
        return HyperVState.UNKNOWN;
      }
      return HyperVState.fromExitCode(res);
    } catch (ExecutionException e) {
      LOG.warn("Exception during Hyper-V state calculation", e);
      return HyperVState.ERROR;
    }
  }

  @NotNull
  private static EnumSet<CpuInfoFlags> calcCpuInfo(@NotNull AndroidSdkHandler handler) {
    try {
      Integer res = runEmulatorCheck("cpu-info", LOWEST_TOOLS_REVISION_CPU_INFO, handler);
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
