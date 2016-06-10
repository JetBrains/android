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
import com.android.tools.fd.client.InstantRunPushFailedException;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class HotSwapTask implements LaunchTask {
  private final Project myProject;
  private final InstantRunContext myInstantRunContext;
  private boolean myNeedsActivityLaunch;

  public HotSwapTask(@NotNull Project project, @NotNull InstantRunContext context) {
    myProject = project;
    myInstantRunContext = context;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Hotswapping changes";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_HOTSWAP;
  }

  @Override
  public boolean perform(@NotNull final IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    InstantRunManager manager = InstantRunManager.get(myProject);
    try {
      myNeedsActivityLaunch = manager.pushArtifacts(device, myInstantRunContext, UpdateMode.HOT_SWAP);
      printer.stdout("Hot swapped changes");
    }
    catch (InstantRunPushFailedException | IOException e) {
      launchStatus.terminateLaunch("Error installing hot swap patches: " + e);
      return false;
    }

    InstantRunStatsService.get(myProject).notifyDeployType(DeployType.HOTSWAP);
    return true;
  }

  public boolean needsActivityLaunch() {
    return myNeedsActivityLaunch;
  }
}
