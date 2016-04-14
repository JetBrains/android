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
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunUserFeedback;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class NoChangesTasksProvider implements LaunchTasksProvider {
  private final AndroidFacet myFacet;

  public NoChangesTasksProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  public List<LaunchTask> getTasks(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter consolePrinter) {
    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(myFacet);
    }
    catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to determine application id for module " + myFacet.getModule().getName());
      return Collections.emptyList();
    }

    // We should update the id on the device even if there were no artifact changes, since otherwise the next build will mismatch
    InstantRunManager.transferLocalIdToDeviceId(device, myFacet.getModule());
    DeployApkTask.cacheManifestInstallationData(device, myFacet, pkgName);

    consolePrinter.stdout("No changes.");
    new InstantRunUserFeedback(myFacet.getModule()).info("No changes to deploy");
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public DebugConnectorTask getConnectDebuggerTask(@NotNull LaunchStatus launchStatus) {
    return null;
  }

  @Override
  public boolean createsNewProcess() {
    return false;
  }
}
