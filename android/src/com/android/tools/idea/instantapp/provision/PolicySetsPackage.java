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
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

class PolicySetsPackage extends ProvisionPackage {
  @NotNull private static final String PKG_DESCRIPTION = "Policy sets";
  @NotNull private static final String APK_PREFIX = "instantapps";
  @NotNull private static final String PKG_NAME = "com.google.android.gms.policy_instantapps";
  @NotNull private static final String APK_SUB_FOLDER = "policy_sets";
  // These are the variants of the apks contained in the SDK. Basically the names of the folders with apks
  @NotNull private static final List<String> SUPPORTED_VARIANTS = Lists.newArrayList("debug");

  PolicySetsPackage(@NotNull File instantAppSdk) {
    super(instantAppSdk);
  }

  @Override
  void install(@NotNull IDevice device) throws ProvisionException {
    if (getOsBuildType(device).compareTo(RELEASE_TYPE) == 0) {
      // Force the device to fetch updated GServices flag values, as certain flags are required to use AIA pre-release
      executeShellCommand(device, "am broadcast -a android.server.checkin.CHECKIN", false /* root is not required */);
    }
    else {
      // TODO: Verify if there's more than one policy and install all of them
      super.install(device);
    }
  }

  @Override
  void setFlags(@NotNull IDevice device, @NotNull String osBuildType) throws ProvisionException {
    getLogger().info("Setting flags for build type \"" + osBuildType + "\"");

    // Set the GServices value for policy sets with the list constructed above
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process /system/bin " +
                        "com.android.commands.am.Am broadcast -a " +
                        "com.google.gservices.intent.action.GSERVICES_OVERRIDE -e " +
                        "gms:chimera:dev_module_packages " + PKG_NAME,
                        true /* root is required */);
    executeShellCommand(device, "am force-stop com.google.android.gms", true /* root is required */);

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
  List<String> getSupportedVariants() {
    return SUPPORTED_VARIANTS;
  }

  @NotNull
  @Override
  String getApkSubFolder() {
    return APK_SUB_FOLDER;
  }

  @NotNull
  @Override
  String getApkPrefix() {
    return APK_PREFIX;
  }

  @NotNull
  @Override
  String getPkgName() {
    return PKG_NAME;
  }

  @NotNull
  @Override
  String getDescription() {
    return PKG_DESCRIPTION;
  }
}
