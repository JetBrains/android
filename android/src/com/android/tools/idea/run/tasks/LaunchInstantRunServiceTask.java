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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class LaunchInstantRunServiceTask implements LaunchTask {
  // Timeout for launching a service via "adb shell am startservice ..". The actual value is arbitrary at this point..
  private static final int LAUNCH_SERVICE_TIME_SECS = 5;

  private final String myAppId;

  public LaunchInstantRunServiceTask(@NotNull String appId) {
    myAppId = appId;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Launching Instant Run Service";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    String startIrServiceCmd = String.format("am startservice %1$s/com.android.tools.fd.runtime.InstantRunService", myAppId);
    ShellCommandLauncher.execute(startIrServiceCmd, device, launchStatus, printer, LAUNCH_SERVICE_TIME_SECS, TimeUnit.SECONDS);
    return true;
  }
}
