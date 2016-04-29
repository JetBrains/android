/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.run.util.LaunchUtils;
import org.jetbrains.annotations.NotNull;

public class DismissKeyguardTask implements LaunchTask {
  @NotNull
  @Override
  public String getDescription() {
    return "Dismissing keyguard";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.ASYNC_TASK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    LaunchUtils.initiateDismissKeyguard(device);
    return true;
  }
}
