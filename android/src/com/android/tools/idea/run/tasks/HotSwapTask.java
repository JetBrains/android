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
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunPushFailedException;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.runners.ExecutionEnvironment;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class HotSwapTask implements LaunchTask {
  private final AndroidFacet myFacet;
  private final ExecutionEnvironment myEnv;
  private boolean myNeedsActivityLaunch;

  public HotSwapTask(@NotNull ExecutionEnvironment env, @NotNull AndroidFacet facet) {
    myEnv = env;
    myFacet = facet;
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
    printer.stdout("Hotswapping changes...");

    InstantRunManager manager = InstantRunManager.get(myFacet.getModule().getProject());

    AndroidGradleModel model = AndroidGradleModel.get(myFacet);
    assert model != null;
    InstantRunBuildInfo buildInfo = InstantRunGradleUtils.getBuildInfo(model);
    assert buildInfo != null;

    try {
      myNeedsActivityLaunch = manager.pushArtifacts(device, myFacet, UpdateMode.HOT_SWAP, buildInfo);
      // Note that the above method will update the build id on the device
      // and the InstalledPatchCache, so we don't have to do it again.
    }
    catch (InstantRunPushFailedException e) {
      launchStatus.terminateLaunch("Error installing hot swap patches: " + e);
      return false;
    }

    InstantRunStatsService.get(myEnv.getProject()).notifyDeployType(InstantRunStatsService.DeployType.HOTSWAP);
    return true;
  }

  public boolean needsActivityLaunch() {
    return myNeedsActivityLaunch;
  }
}
