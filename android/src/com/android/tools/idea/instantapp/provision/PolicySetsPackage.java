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

import com.android.ddmlib.IDevice;
import com.android.utils.FileUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class PolicySetsPackage extends ProvisionPackage {
  @NotNull private static final String PKG_DESCRIPTION = "Policy sets";
  @NotNull private final List<PolicyPackage> myPolicyPackages;

  PolicySetsPackage(@NotNull File instantAppSdk) {
    super(instantAppSdk);
    myPolicyPackages = new LinkedList<>();

    File policyFolder = FileUtils.join(instantAppSdk, "tools", "apks", "debug", "policy_sets");
    assert policyFolder.exists() && policyFolder.isDirectory();
    Set<String> apks = new HashSet<>();
    for (String apk : policyFolder.list()) {
      apk = apk.replaceAll("_(arm64-v8a|x86)\\.apk", "");
      if (!apks.contains(apk)) {
        apks.add(apk);
        myPolicyPackages.add(new PolicyPackage(instantAppSdk, apk));
      }
    }
  }

  @Override
  boolean shouldInstall(@NotNull IDevice device) throws ProvisionException {
    for (PolicyPackage pack : myPolicyPackages) {
      if (pack.shouldInstall(device)) {
        return true;
      }
    }
    return false;
  }

  @Override
  void install(@NotNull IDevice device) throws ProvisionException {
    if (getOsBuildType(device).compareTo(RELEASE_TYPE) == 0) {
      // Force the device to fetch updated GServices flag values, as certain flags are required to use AIA pre-release
      executeShellCommand(device, "am broadcast -a android.server.checkin.CHECKIN", false /* root is not required */);
    }
    else {
      for (PolicyPackage pack : myPolicyPackages) {
        if (pack.shouldInstall(device)) {
          pack.install(device);
        }
      }
      setFlags(device, getOsBuildType(device));
    }
  }

  @Override
  void setFlags(@NotNull IDevice device, @NotNull String osBuildType) throws ProvisionException {
    getLogger().info("Setting flags for build type \"" + osBuildType + "\"");

    if (!myPolicyPackages.isEmpty()) {
      String allPolicyPackages = String.join(",", Lists.transform(myPolicyPackages, PolicyPackage::getPkgName));

      // Set the GServices value for policy sets with the list constructed above
      executeShellCommand(device,
                          "CLASSPATH=/system/framework/am.jar su root app_process /system/bin " +
                          "com.android.commands.am.Am broadcast -a " +
                          "com.google.gservices.intent.action.GSERVICES_OVERRIDE -e " +
                          "gms:chimera:dev_module_packages " + allPolicyPackages,
                          true /* root is required */);
      executeShellCommand(device, "am force-stop com.google.android.gms", true /* root is required */);
    }

    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e com.google.android.instantapps.common.useIsotope true",
                        true /* root is required */);
    executeShellCommand(device, "am force-stop com.google.android.instantapps.supervisor", true /* root is required */);
  }

  @NotNull
  @Override
  String getApkPrefix() {
    return "";
  }

  @NotNull
  @Override
  String getPkgName() {
    return "";
  }

  @NotNull
  @Override
  String getDescription() {
    return PKG_DESCRIPTION;
  }

  @NotNull
  @VisibleForTesting
  List<? extends ProvisionPackage> getPolicyPackages() {
    return myPolicyPackages;
  }

  static class PolicyPackage extends ProvisionPackage {
    @NotNull private static final String PKG_DESCRIPTION_PREFIX = "Policy set ";
    @NotNull private static final String PKG_NAME_PREFIX = "com.google.android.gms.policy_";
    @NotNull private static final String APK_SUB_FOLDER = "policy_sets";
    // These are the variants of the apks contained in the SDK. Basically the names of the folders with apks
    @NotNull private static final List<String> SUPPORTED_VARIANTS = Lists.newArrayList("debug");

    @NotNull private final String myApkPrefix;

    PolicyPackage(@NotNull File instantAppSdk, @NotNull String apkPrefix) {
      super(instantAppSdk);
      myApkPrefix = apkPrefix;
    }

    @NotNull
    @Override
    String getApkPrefix() {
      return myApkPrefix;
    }

    @NotNull
    @Override
    String getPkgName() {
      return PKG_NAME_PREFIX + myApkPrefix;
    }

    @NotNull
    @Override
    String getDescription() {
      return PKG_DESCRIPTION_PREFIX + myApkPrefix;
    }

    @Override
    @NotNull
    List<String> getSupportedVariants() {
      return SUPPORTED_VARIANTS;
    }

    @Override
    @NotNull
    String getApkSubFolder() {
      return APK_SUB_FOLDER;
    }
  }
}
