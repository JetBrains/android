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
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A generic hypervisor state monitor object.
 * The HypervisorStatsMonitor class implements the monitoring of the
 * state of hypervisors we care about and reports it using the metric
 * reporting API
 *
 * Currently the only implemented check is for the HyperV state on Windows
 */
public class HypervisorStatsMonitor {

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

  // Upload and recalculate the state couple times a day, so we could have some
  // confidence that we drop at least one valid metric in 24 hours
  private static final int UPLOAD_INTERVAL_HOURS = 6;
  private static final Revision LOWEST_TOOLS_REVISION = new Revision(25, 0, 3);

  private AndroidSdkHandler mySdkHandler = null;
  private ScheduledFuture<?> myUploadTask = null;

  private HyperVState hyperVState = HyperVState.UNKNOWN;

  public void start() {
    if (!SystemInfo.isWindows) {
      // currently we only report a hyper-v state, and that doesn't exist outside of Windows
      return;
    }

    if (!UsageTracker.getInstance().canTrack()) {
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
    if (!UsageTracker.getInstance().canTrack()) {
      myUploadTask.cancel(true);
      return;
    }

    if (mySdkHandler == null) {
      mySdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    }

    updateHyperVState();

    UsageTracker.getInstance().trackHypervisorStats(hyperVState.toString());
  }


  private void updateHyperVState() {
    if (hyperVState != HyperVState.UNKNOWN && hyperVState != HyperVState.ERROR) {
      // once calculated, it won't change until the OS restart
      return;
    }

    hyperVState = calcHyperVState(mySdkHandler);
  }

  @NotNull
  private static File getEmulatorCheckBinary(@NotNull AndroidSdkHandler handler) {
    return new File(handler.getLocation(), FileUtil.join(SdkConstants.OS_SDK_TOOLS_FOLDER, SdkConstants.FN_EMULATOR_CHECK));
  }

  @NotNull
  private static HyperVState calcHyperVState(@NotNull AndroidSdkHandler handler) {
    LocalPackage toolsPackage = handler.getLocalPackage(SdkConstants.FD_TOOLS,
                                                        new StudioLoggerProgressIndicator(AndroidSdkInitializer.class));
    if (toolsPackage == null) {
      return HyperVState.UNKNOWN;
    }

    final Revision toolsRevision = toolsPackage.getVersion();
    if (toolsRevision.compareTo(LOWEST_TOOLS_REVISION) < 0) {
      return HyperVState.UNKNOWN; // we've got too old version of sdktools
    }

    File checkBinary = getEmulatorCheckBinary(handler);
    if (!checkBinary.isFile()) {
      return HyperVState.UNKNOWN; // well, if it's not here, we can't to a thing
    }
    GeneralCommandLine commandLine = new GeneralCommandLine(checkBinary.getPath(), "hyper-v");
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      ProcessOutput output = process.runProcess();
      int exitCode = output.getExitCode();
      return HyperVState.fromExitCode(exitCode);
    }
    catch (ExecutionException e) {
      return HyperVState.UNKNOWN;
    }
  }
}
