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

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_TAG_ID;
import static com.android.tools.idea.avdmanager.AvdManagerConnection.isFoldable;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AvdLaunchEvent;
import com.google.wireless.android.sdk.stats.AvdLaunchEvent.AvdClass;
import com.google.wireless.android.sdk.stats.AvdLaunchEvent.LaunchType;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmulatorRunner {
  private final GeneralCommandLine myCommandLine;

  private ProcessHandler myProcessHandler;
  private final List<ProcessListener> myExtraListeners = new ArrayList<>();

  public EmulatorRunner(@NotNull GeneralCommandLine commandLine, @Nullable AvdInfo avdInfo) {
    myCommandLine = commandLine;

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
      .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
      .setKind(AndroidStudioEvent.EventKind.DEPLOYMENT_TO_EMULATOR);

    event.setAvdLaunchEvent(AvdLaunchEvent.newBuilder()
                              .setLaunchType(getLaunchType(commandLine))
                              .setAvdClass(getAvdClass(avdInfo)));

    if (avdInfo != null) {
      event.setDeviceInfo(DeviceInfo.newBuilder()
                            .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(avdInfo.getAbiType()))
                            .setBuildApiLevelFull(avdInfo.getAndroidVersion().toString()));
    }

    UsageTracker.log(event);
  }

  public ProcessHandler start() throws ExecutionException {
    final Process process = myCommandLine.createProcess();
    myProcessHandler = new EmulatorProcessHandler(process, myCommandLine);
    myExtraListeners.forEach(myProcessHandler::addProcessListener);
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

  @NotNull
  private static LaunchType getLaunchType(@NotNull GeneralCommandLine commandLine) {
    return commandLine.getParametersList().getParameters().contains("-qt-hide-window") ? LaunchType.IN_TOOL_WINDOW : LaunchType.STANDALONE;
  }

  @NotNull
  private static AvdClass getAvdClass(@Nullable AvdInfo avdInfo) {
    if (avdInfo == null) {
      return AvdClass.UNKNOWN_AVD_CLASS;
    }
    String tag = avdInfo.getProperty(AVD_INI_TAG_ID);
    if ("android-tv".equals(tag)) {
      return AvdClass.TV;
    }
    if ("android-automotive".equals(tag)) {
      return AvdClass.AUTOMOTIVE;
    }
    if ("android-wear".equals(tag)) {
      return AvdClass.WEARABLE;
    }
    if (isFoldable(avdInfo)) {
      return AvdClass.FOLDABLE;
    }
    return AvdClass.GENERIC;
  }
}
