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
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunPushFailedException;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.fd.InstantRunUserFeedback;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.NotificationType;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class DexDeployTask implements LaunchTask {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final InstantRunBuildInfo myBuildInfo;
  @NotNull private final ExecutionEnvironment myEnv;

  public DexDeployTask(@NotNull ExecutionEnvironment env, @NotNull AndroidFacet facet, @NotNull InstantRunBuildInfo buildInfo) {
    myEnv = env;
    myFacet = facet;
    myBuildInfo = buildInfo;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing restart patches";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_APK;
  }

  @Override
  public boolean perform(@NotNull final IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      try {
        InstantRunManager manager = InstantRunManager.get(myFacet.getModule().getProject());
        manager.pushArtifacts(device, myFacet, UpdateMode.HOT_SWAP, myBuildInfo);
        // Note that the above method will update the build id on the device
        // and the InstalledPatchCache, so we don't have to do it again.

        InstantRunStatsService.get(myFacet.getModule().getProject())
          .notifyDeployType(InstantRunStatsService.DeployType.DEX);

        String status = "Instant run applied code changes and restarted the app.";
        new InstantRunUserFeedback(myFacet.getModule()).postHtml(NotificationType.INFORMATION, status, null);

        return true;
      }
      catch (InstantRunPushFailedException e) {
        launchStatus.terminateLaunch("Error installing cold swap patches: " + e);
        InstantRunManager.LOG.warn("Failed to push dex files: ", e);

        return false;
      }
  }
}
