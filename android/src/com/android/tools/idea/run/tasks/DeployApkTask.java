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
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunStatsService;
import com.android.tools.idea.gradle.run.GradleInstantRunContext;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class DeployApkTask implements LaunchTask {
  private static final Logger LOG = Logger.getInstance(DeployApkTask.class);

  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;
  private final boolean myInstantRunAware;

  public DeployApkTask(@NotNull AndroidFacet facet, @NotNull LaunchOptions launchOptions, @NotNull ApkProvider apkProvider) {
    this(facet, launchOptions, apkProvider, false);
  }

  public DeployApkTask(@NotNull AndroidFacet facet, @NotNull LaunchOptions launchOptions, @NotNull ApkProvider apkProvider,
                       boolean instantRunAware) {
    myFacet = facet;
    myLaunchOptions = launchOptions;
    myApkProvider = apkProvider;
    myInstantRunAware = instantRunAware;
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
    Collection<ApkInfo> apks;
    try {
      apks = myApkProvider.getApks(device);
    }
    catch (ApkProvisionException e) {
      printer.stderr(e.getMessage());
      LOG.warn(e);
      return false;
    }

    ApkInstaller installer = new ApkInstaller(myFacet, myLaunchOptions, ServiceManager.getService(InstalledApkCache.class), printer);
    for (ApkInfo apk : apks) {
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

      if (myInstantRunAware) {
        GradleInstantRunContext context = new GradleInstantRunContext(pkgName, myFacet);
        InstantRunManager.transferLocalIdToDeviceId(device, context);
        cacheManifestInstallationData(device, context);
      }
      else {
        // If not using IR, we need to transfer an empty build id over to the device. This assures that a subsequent IR
        // will not somehow see a stale build id on the device.
        try {
          InstantRunClient.transferBuildIdToDevice(device, "", pkgName, null);
        }
        catch (Throwable ignored) {
        }
      }
    }

    trackInstallation(device);
    InstantRunStatsService.get(myFacet.getModule().getProject())
      .notifyDeployType(myInstantRunAware ? InstantRunStatsService.DeployType.FULLAPK : InstantRunStatsService.DeployType.LEGACY);

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
