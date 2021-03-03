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
package com.android.tools.idea.run.tasks;

import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import org.jetbrains.annotations.NotNull;


public class StartLiveLiteralMonitoringTask implements LaunchTask {
  private static final String ID = "LIVE_LITERAL_MONITORING";

  @Nullable private final Runnable myStartLiveUpdate;

  public StartLiveLiteralMonitoringTask(@Nullable Runnable startLiveUpdate) {
    myStartLiveUpdate = startLiveUpdate;
  }

  @Override
  public @NotNull String getDescription() {
    return "Starting Live Literal Monitoring";
  }

  @Override
  public int getDuration() {
    return 1;
  }

  @Override
  public LaunchResult run(@NotNull LaunchContext launchContext) {
    if (myStartLiveUpdate != null) {
      myStartLiveUpdate.run();
    }
    // Monitoring should always successfully starts.
    return LaunchResult.success();
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }
}
