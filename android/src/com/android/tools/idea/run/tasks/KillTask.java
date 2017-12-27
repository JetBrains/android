/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ddmlib.*;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class KillTask implements LaunchTask {
  private final Project myProject;
  private final InstantRunContext myContext;

  public KillTask(@NotNull Project project, @NotNull InstantRunContext context) {
    myProject = project;
    myContext = context;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Restart application";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.ASYNC_TASK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    try {
      NullOutputReceiver receiver = new NullOutputReceiver();
      device.executeShellCommand("am force-stop " + myContext.getApplicationId(), receiver);
      // app will be started by AndroidRunConfiguration
      return true;
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
      launchStatus.terminateLaunch("Error stopping application: " + e);
      InstantRunManager.LOG.warn("Failed stopping application: ", e);

      e.printStackTrace();
      return false;
    }
  }
}
