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

class DevManPackage extends ProvisionPackage {
  @NotNull private static final String PKG_DESCRIPTION = "DevMan";
  @NotNull private static final String APK_PREFIX = "devman";
  @NotNull private static final String PKG_NAME = "com.google.android.instantapps.devman";
  // These are the variants of the apks contained in the SDK. Basically the names of the folders with apks
  @NotNull private static final List<String> SUPPORTED_VARIANTS = Lists.newArrayList("debug");

  DevManPackage(@NotNull File instantAppSdk) {
    super(instantAppSdk);
  }

  @Override
  void install(@NotNull IDevice device) throws ProvisionException {
    if (!getInstalledApkVersion(device).isEmpty()) {
      uninstall(device);
    }
    super.install(device);
    executeShellCommand(device, "pm grant " + PKG_NAME + " android.permission.READ_EXTERNAL_STORAGE", false /* root is not required */);
    executeShellCommand(device, "am force-stop com.google.android.gms", false /* root is not required */);
  }

  @NotNull
  @Override
  List<String> getSupportedVariants() {
    return SUPPORTED_VARIANTS;
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

  @Override
  boolean isArchSpecificApk() {
    return false;
  }

  @NotNull
  @Override
  String getDescription() {
    return PKG_DESCRIPTION;
  }
}
