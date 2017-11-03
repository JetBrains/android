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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EmulatorRunner {
  private final GeneralCommandLine myCommandLine;

  private ProcessHandler myProcessHandler;
  private final List<ProcessListener> myExtraListeners = new ArrayList<>();

  public EmulatorRunner(@NotNull GeneralCommandLine commandLine, @Nullable AvdInfo avdInfo) {
    myCommandLine = commandLine;

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
      .setKind(AndroidStudioEvent.EventKind.DEPLOYMENT_TO_EMULATOR);

    if (avdInfo != null) {
      event.setDeviceInfo(DeviceInfo.newBuilder()
                            .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(avdInfo.getAbiType()))
                            .setBuildApiLevelFull(avdInfo.getAndroidVersion().toString()));
    }

    UsageTracker.getInstance().log(event);
  }

  public ProcessHandler start() throws ExecutionException {
    final Process process = myCommandLine.createProcess();
    myProcessHandler = new EmulatorProcessHandler(process, myCommandLine);
    myProcessHandler.startNotify();
    return myProcessHandler;
  }

  /**
   * Adds a listener to our process (if it's started already), or saves the listener away to be added when the process is started.
   */
  public void addProcessListener(@NotNull ProcessListener listener) {
    if (myProcessHandler != null) {
      myProcessHandler.addProcessListener(listener);
    }
    else {
      myExtraListeners.add(listener);
    }
  }

  public static class ProcessOutputCollector extends ProcessAdapter {
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      sb.append(event.getText());
    }

    public String getText() {
      return sb.toString();
    }
  }
}
