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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.FastDeployManager;
import com.google.common.hash.HashCode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class InstantRunAwareApkInstaller implements PackageInstaller {
  private static final Logger LOG = Logger.getInstance(InstantRunAwareApkInstaller.class);

  private final AndroidFacet myFacet;
  private final ApkProvider myApkProvider;
  private final LaunchOptions myLaunchOptions;

  public InstantRunAwareApkInstaller(@NotNull AndroidFacet facet, @NotNull LaunchOptions launchOptions, @NotNull ApkProvider apkProvider) {
    myFacet = facet;
    myLaunchOptions = launchOptions;
    myApkProvider = apkProvider;
  }

  @Override
  public boolean install(@NotNull IDevice device, @NotNull AtomicBoolean cancelInstallation, @NotNull ConsolePrinter printer) {
    Collection<ApkInfo> apks;
    try {
      apks = myApkProvider.getApks(device);
    }
    catch (ApkProvisionException e) {
      printer.stderr(e.getMessage());
      LOG.warn(e);
      return false;
    }

    File arsc = FastDeployManager.findResourceArsc(myFacet);
    long arscTimestamp = arsc == null ? 0 : arsc.lastModified();
    File manifest = FastDeployManager.findMergedManifestFile(myFacet);
    long manifestTimeStamp = manifest == null ? 0L : manifest.lastModified();

    ApkInstaller installer = new ApkInstaller(myFacet, myLaunchOptions, ServiceManager.getService(InstalledApkCache.class), printer);
    for (ApkInfo apk : apks) {
      if (!apk.getFile().exists()) {
        String message = "The APK file " + apk.getFile().getPath() + " does not exist on disk.";
        printer.stderr(message);
        LOG.error(message);
        return false;
      }

      String pkgName = apk.getApplicationId();
      if (!installer.uploadAndInstallApk(device, pkgName, apk.getFile(), cancelInstallation)) {
        return false;
      }

      InstalledPatchCache patchCache = ServiceManager.getService(InstalledPatchCache.class);
      patchCache.setInstalledArscTimestamp(device, pkgName, arscTimestamp);
      patchCache.setInstalledManifestTimestamp(device, pkgName, manifestTimeStamp);
      HashCode currentHash = InstalledPatchCache.computeManifestResources(myFacet);
      patchCache.setInstalledManifestResourcesHash(device, pkgName, currentHash);
    }

    return true;
  }
}
