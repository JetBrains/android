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
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.fd.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunPushFailedException;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.editor.DefaultActivityLaunch;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DexDeployTask implements LaunchTask {
  @NotNull private final String myPkgName;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final InstantRunBuildInfo myBuildInfo;

  public DexDeployTask(@NotNull String pkgName, @NotNull AndroidFacet facet, @NotNull InstantRunBuildInfo buildInfo) {
    myPkgName = pkgName;
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
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      try {
        InstantRunManager.displayVerifierStatus(myFacet, myBuildInfo);

        InstantRunManager manager = InstantRunManager.get(myFacet.getModule().getProject());
        boolean restart = manager.pushArtifacts(device, myFacet, UpdateMode.HOT_SWAP, myBuildInfo);
        // Note that the above method will update the build id on the device
        // and the InstalledPatchCache, so we don't have to do it again.

        if (restart) {
          // Trigger an activity start/restart.
          // TODO: Clean this up such that the DeployTask just specifies whether an activity
          // launch is required afterwards.
          LaunchTask launchTask = DefaultActivityLaunch.INSTANCE.createState().getLaunchTask(myPkgName, myFacet, false, "");
          launchTask.perform(device, launchStatus, printer);
        }

        return true;
      }
      catch (InstantRunPushFailedException e) {
        launchStatus.terminateLaunch("Error installing cold swap patches: " + e);
        return false;
      }
  }
}
