/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp.provision;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.NullOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.apk.viewer.AaptInvoker;
import com.android.tools.idea.apk.viewer.AndroidApplicationInfo;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

abstract class ProvisionPackage {
  @NotNull private static final List<String> SUPPORTED_ARCHITECTURES = Lists.newArrayList("x86", "arm64-v8a");
  // These are the variants of the apks contained in the SDK. Basically the names of the folders with apks
  @NotNull private static final List<String> SUPPORTED_VARIANTS = Lists.newArrayList("release", "debug");
  @NotNull private static final String OS_BUILD_TYPE_PROPERTY = "ro.build.tags";
  @NotNull static final String DEV_TYPE = "dev-keys";
  @NotNull static final String TEST_TYPE = "test-keys";
  @NotNull static final String RELEASE_TYPE = "release-keys";
  @NotNull private static final Collection<String> POSSIBLE_OS_BUILD_TYPES = Lists.newArrayList(DEV_TYPE, TEST_TYPE, RELEASE_TYPE);
  private static final int MIN_API_LEVEL = 23;

  @NotNull private final File myInstantAppSdk;

  ProvisionPackage(@NotNull File instantAppSdk) {
    myInstantAppSdk = instantAppSdk;
  }

  boolean shouldInstall(@NotNull IDevice device) throws ProvisionException {
    // TODO: is it always the same version for different variants?
    File apk = getApk(getDeviceArchitecture(device), getSupportedVariants().get(0));
    checkApiLevel(device);
    String apkVersion = getApkVersion(apk);
    String installedApkVersion = getInstalledApkVersion(device);

    getLogger().info("SDK apk version is \"" + apkVersion + "\"");
    getLogger().info("Installed apk version is \"" + (installedApkVersion.isEmpty() ? "not installed" : installedApkVersion) + "\"");
    // Checks if the apk version in the SDK is higher than the installed one. If no installed one, it should always be true.
    return installedApkVersion.isEmpty() || isHigherVersion(apkVersion, installedApkVersion);
    // TODO: should downgrade?
  }

  void install(@NotNull IDevice device) throws ProvisionException {
    for (String variant : getSupportedVariants()) {
      File apk = getApk(getDeviceArchitecture(device), variant);
      try {
        device.installPackage(apk.getPath(), true /* allow reinstall */, "-d" /* allow downgrade */);
      }
      catch (InstallException e) {
        getLogger().info("APK " + apk + "failed to install. Trying other variant if available", e);
        continue;
      }
      setFlags(device, getOsBuildType(device));
      return;
    }
    throw new ProvisionException("Couldn't install package " + getPkgName());
  }

  void uninstall(@NotNull IDevice device) throws ProvisionException {
    try {
      device.uninstallPackage(getPkgName());
    }
    catch (InstallException e) {
      throw new ProvisionException("Couldn't uninstall package " + getPkgName(), e);
    }
  }

  @NotNull
  private static String getDeviceArchitecture(@NotNull IDevice device) throws ProvisionException {
    List<String> architectures = device.getAbis();
    for (String arch : architectures) {
      if (SUPPORTED_ARCHITECTURES.contains(arch)) {
        return arch;
      }
    }
    throw new ProvisionException("Device architecture not supported. Supported architectures are: " + SUPPORTED_ARCHITECTURES);
  }

  @NotNull
  File getApk(@NotNull String arch, @NotNull String variant) throws ProvisionException {
    String path = "tools/apks/" + variant + "/" +
                  getApkSubFolder() + (getApkSubFolder().isEmpty() ? "" : "/") +
                  getApkPrefix() + (isArchSpecificApk() ? "_" + arch : "") + ".apk";
    path = path.replace("/", File.separator);
    File apk = new File(myInstantAppSdk, path);

    if (!apk.exists() || !apk.isFile()) {
      throw new ProvisionException("Apk file " + apk.getAbsolutePath() + "not present in the SDK");
    }

    return apk;
  }

  void setFlags(@NotNull IDevice device, @NotNull String osBuildType) throws ProvisionException {}

  @NotNull
  static String getOsBuildType(@NotNull IDevice device) throws ProvisionException {
    String osBuildType = device.getProperty(OS_BUILD_TYPE_PROPERTY);
    if (osBuildType == null || !POSSIBLE_OS_BUILD_TYPES.contains(osBuildType)) {
      throw new ProvisionException("Device OS build type not supported. Supported types are: " + POSSIBLE_OS_BUILD_TYPES);
    }
    return osBuildType;
  }

  private static void checkApiLevel(@NotNull IDevice device) throws ProvisionException {
    AndroidVersion androidVersion = device.getVersion();
    if (!androidVersion.isGreaterOrEqualThan(MIN_API_LEVEL)) {
      throw new ProvisionException("Device API level must be higher or equal " + MIN_API_LEVEL);
    }
  }

  void executeShellCommand(@NotNull IDevice device, @NotNull String command, boolean rootRequired) throws ProvisionException {
    try {
      if (rootRequired && !device.isRoot()) {
        device.root();
      }
      device.executeShellCommand(command, new NullOutputReceiver());
    }
    catch (Exception e) {
      throw new ProvisionException("Couldn't execute shell command while configuring " + getPkgName(), e);
    }
  }

  @NotNull
  static String getApkVersion(@NotNull File apk) throws ProvisionException {
    try {
      AaptInvoker invoker = AaptInvoker.getInstance();
      if (invoker == null) {
        throw new ProvisionException("Couldn't get AaptInvoker");
      }

      ProcessOutput xmlTree = invoker.getXmlTree(apk, SdkConstants.FN_ANDROID_MANIFEST_XML);
      return AndroidApplicationInfo.fromXmlTree(xmlTree).versionName;
    }
    catch (ExecutionException e) {
      throw new ProvisionException("Couldn't run aapt", e);
    }
  }

  @NotNull
  String getInstalledApkVersion(@NotNull IDevice device) throws ProvisionException {
    try {
      return PackageVersionFinder.getVersion(device, getPkgName());
    }
    catch (Exception e) {
      throw new ProvisionException(e);
    }
  }

  @NotNull
  String getApkSubFolder() {
    // Folder inside the variant folder
    return "";
  }

  boolean isHigherVersion(@NotNull String a, @NotNull String b) {
    String[] as = a.split("\\.|-");
    String[] bs = b.split("\\.|-");
    if (as.length != bs.length) {
      // Reinstall just in case
      return true;
    }
    for (int i = 0; i < as.length; i++) {
      if (as[i].matches("[-+]?\\d*\\.?\\d+") &&
          bs[i].matches("[-+]?\\d*\\.?\\d+") &&
          Integer.parseInt(as[i]) > Integer.parseInt(bs[i])) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  List<String> getSupportedVariants() {
    return SUPPORTED_VARIANTS;
  }

  boolean isArchSpecificApk() {
    return true;
  }

  @NotNull
  abstract String getApkPrefix();

  @NotNull
  abstract String getPkgName();

  @NotNull
  abstract String getDescription();

  @NotNull
  Logger getLogger() {
    return Logger.getInstance(getClass());
  }
}
