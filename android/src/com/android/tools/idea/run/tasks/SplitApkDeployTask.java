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
import com.android.ddmlib.InstallException;
import com.android.tools.fd.client.InstantRunArtifact;
import com.android.tools.fd.client.InstantRunArtifactType;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SplitApkDeployTask implements LaunchTask {
  @NotNull private final String myPkgName;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final InstantRunBuildInfo myBuildInfo;

  public SplitApkDeployTask(@NotNull String pkgName, @NotNull AndroidFacet facet, @NotNull InstantRunBuildInfo buildInfo) {
    myPkgName = pkgName;
    myFacet = facet;
    myBuildInfo = buildInfo;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APKs";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_APK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    InstantRunManager.displayVerifierStatus(myFacet, myBuildInfo);

    List<InstantRunArtifact> artifacts = myBuildInfo.getArtifacts();

    List<String> installOptions = Lists.newArrayList(); // TODO: should we pass in pm install options?
    if (!myBuildInfo.hasMainApk()) {
      installOptions.add("-p"); // partial install
      installOptions.add(myPkgName);
    }

    List<File> apks = Lists.newArrayListWithExpectedSize(artifacts.size());
    for (InstantRunArtifact artifact : artifacts) {
      if (artifact.type == InstantRunArtifactType.SPLIT_MAIN || artifact.type == InstantRunArtifactType.SPLIT) {
        apks.add(artifact.file);
      }
    }

    String cmd = getAdbInstallCommand(apks, installOptions);
    printer.stdout(cmd);
    InstantRunManager.LOG.info(cmd);

    try {
      device.installPackages(apks, true, installOptions, 5, TimeUnit.MINUTES);

      InstantRunManager.transferLocalIdToDeviceId(device, myFacet.getModule());
      DeployApkTask.cacheManifestInstallationData(device, myFacet, myPkgName);

      InstantRunStatsService.get(myFacet.getModule().getProject())
        .notifyDeployType(InstantRunStatsService.DeployType.SPLITAPK);
      return true;
    }
    catch (InstallException e) {
      launchStatus.terminateLaunch("Error installing split apks: " + e);
      return false;
    }
  }

  @NotNull
  private static String getAdbInstallCommand(@NotNull List<File> apks, @NotNull List<String> installOptions) {
    StringBuilder sb = new StringBuilder();
    sb.append("$ adb install-multiple -r ");
    if (!installOptions.isEmpty()) {
      sb.append(Joiner.on(' ').join(installOptions));
      sb.append(' ');
    }

    for (File f : apks) {
      sb.append(f.getPath());
      sb.append(' ');
    }

    return sb.toString();
  }
}
