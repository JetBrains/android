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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.ir.client.InstantRunClient;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class DeployApkTask implements LaunchTask {
  private static final Logger LOG = Logger.getInstance(DeployApkTask.class);

  private final Project myProject;
  private final Collection<ApkInfo> myApks;
  private final LaunchOptions myLaunchOptions;
  private final InstantRunContext myInstantRunContext;

  public DeployApkTask(@NotNull Project project, @NotNull LaunchOptions launchOptions, @NotNull Collection<ApkInfo> apks) {
    this(project, launchOptions, apks, null);
  }

  public DeployApkTask(@NotNull Project project, @NotNull LaunchOptions launchOptions, @NotNull Collection<ApkInfo> apks,
                       @Nullable InstantRunContext instantRunContext) {
    myProject = project;
    myLaunchOptions = launchOptions;
    myApks = apks;
    myInstantRunContext = instantRunContext;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APK";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.DEPLOY_APK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    FullApkInstaller
      installer = new FullApkInstaller(myProject, myLaunchOptions, ServiceManager.getService(InstalledApkCache.class), printer);
    for (ApkInfo apk : myApks) {
      if (!apk.getFile().exists()) {
        String message = "The APK file " + apk.getFile().getPath() + " does not exist on disk.";
        printer.stderr(message);
        LOG.warn(message);
        return false;
      }

      String pkgName = apk.getApplicationId();
      if (!installer.uploadAndInstallApk(device, pkgName, apk.getFile(), launchStatus)) {
        return false;
      }

      if (myInstantRunContext == null) {
        // If not using IR, we need to transfer an empty build id over to the device. This assures that a subsequent IR
        // will not somehow see a stale build id on the device.
        try {
          InstantRunClient.transferBuildIdToDevice(device, "", pkgName, null);
        }
        catch (Throwable ignored) {
        }
      }
    }

    if (myInstantRunContext == null) {
      InstantRunStatsService.get(myProject).notifyNonInstantRunDeployType(device);
    } else {
      InstantRunStatsService.get(myProject).notifyDeployType(DeployType.FULLAPK, myInstantRunContext, device);
    }
    trackInstallation(device);

    return true;
  }

  public static void cacheManifestInstallationData(@NotNull IDevice device, @NotNull InstantRunContext context) {
    InstalledPatchCache patchCache = ServiceManager.getService(InstalledPatchCache.class);
    patchCache.setInstalledManifestResourcesHash(device, context.getApplicationId(), context.getManifestResourcesHash());
  }

  private static void trackInstallation(@NotNull IDevice device) {
    if (!UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn()) {
      return;
    }
    UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
       .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
       .setKind(AndroidStudioEvent.EventKind.DEPLOYMENT_APK)
       .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(device)));
  }
}
