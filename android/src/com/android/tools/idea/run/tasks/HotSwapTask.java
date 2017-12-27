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
import com.android.tools.ir.client.InstantRunClient;
import com.android.tools.ir.client.InstantRunPushFailedException;
import com.android.tools.ir.client.UpdateMode;
import com.android.tools.idea.fd.DeployType;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class HotSwapTask implements LaunchTask {
  private final Project myProject;
  private final InstantRunContext myInstantRunContext;
  private final boolean myRestartActivity;

  public HotSwapTask(@NotNull Project project, @NotNull InstantRunContext context, boolean restartActivity) {
    myProject = project;
    myInstantRunContext = context;
    myRestartActivity = restartActivity;
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
    UpdateMode updateMode;
    try {
      InstantRunClient instantRunClient = InstantRunManager.getInstantRunClient(myInstantRunContext);
      if (instantRunClient == null) {
        return terminateLaunch(launchStatus, "Unable to connect to application. Press Run or Debug to rebuild and install the app.");
      }

      updateMode = manager.pushArtifacts(device, myInstantRunContext, myRestartActivity ? UpdateMode.WARM_SWAP : UpdateMode.HOT_SWAP);
      printer.stdout("Hot swapped changes, activity " + (updateMode == UpdateMode.HOT_SWAP ? "not restarted" : "restarted"));
    }
    catch (InstantRunPushFailedException | IOException e) {
      return terminateLaunch(launchStatus, "Error installing hot swap patches: " + e);
    }

    InstantRunStatsService.get(myProject).notifyDeployType(DeployType.HOTSWAP, myInstantRunContext, device);
    return true;
  }

  private static boolean terminateLaunch(LaunchStatus launchStatus, String msg) {
    launchStatus.terminateLaunch(msg);
    InstantRunManager.LOG.info("Terminating launch: " + msg);
    return false;
  }
}
