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
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.idea.fd.*;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
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
    ApkInstaller installer = new ApkInstaller(myProject, myLaunchOptions, ServiceManager.getService(InstalledApkCache.class), printer);
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
      InstantRunStatsService.get(myProject).notifyDeployType(DeployType.LEGACY, BuildCause.NO_INSTANT_RUN, device);
    } else {
      InstantRunStatsService.get(myProject).notifyDeployType(DeployType.FULLAPK, myInstantRunContext.getBuildSelection().why, device);
    }
    trackInstallation(device);

    return true;
  }

  public static void cacheManifestInstallationData(@NotNull IDevice device, @NotNull InstantRunContext context) {
    InstalledPatchCache patchCache = ServiceManager.getService(InstalledPatchCache.class);
    patchCache.setInstalledManifestResourcesHash(device, context.getApplicationId(), context.getManifestResourcesHash());
  }

  private static int ourInstallationCount = 0;

  private static void trackInstallation(@NotNull IDevice device) {
    if (!UsageTracker.getInstance().canTrack()) {
      return;
    }

    // only track every 20th installation (just to reduce the load on the server)
    ourInstallationCount = (ourInstallationCount + 1) % 20;
    if (ourInstallationCount != 0) {
      return;
    }

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEPLOYMENT, UsageTracker.ACTION_DEPLOYMENT_APK, null, null);

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_SERIAL_HASH,
                                          Hashing.md5().hashString(device.getSerialNumber(), Charsets.UTF_8).toString(), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_TAGS,
                                          device.getProperty(IDevice.PROP_BUILD_TAGS), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_TYPE,
                                          device.getProperty(IDevice.PROP_BUILD_TYPE), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_VERSION_RELEASE,
                                          device.getProperty(IDevice.PROP_BUILD_VERSION), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_BUILD_API_LEVEL,
                                          device.getProperty(IDevice.PROP_BUILD_API_LEVEL), null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_CPU_ABI,
                                          device.getProperty(IDevice.PROP_DEVICE_CPU_ABI), null);

    String manufacturer = device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER);
    String model = device.getProperty(IDevice.PROP_DEVICE_MODEL);
    String manufacturerModel = manufacturer + "-" + model;

    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_MANUFACTURER,
                                          manufacturer, null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_MODEL,
                                          model, null);
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_DEVICE_INFO, UsageTracker.DEVICE_INFO_MANUFACTURER_MODEL,
                                          manufacturerModel, null);
  }
}
