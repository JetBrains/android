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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

class SupervisorPackage extends ProvisionPackage {
  @NotNull private static final String APK_PREFIX = "supervisor";
  @NotNull private static final String PKG_NAME = "com.google.android.instantapps.supervisor";
  @NotNull private static final String ARM_V8 = "arm64-v8a";
  @NotNull private static final String ARM_V7 = "armeabi-v7a";
  // These are the variants of the apks contained in the SDK. Basically the names of the folders with apks
  @NotNull private static final List<String> SUPPORTED_VARIANTS = Lists.newArrayList("release");

  SupervisorPackage(@NotNull File instantAppSdk) {
    super(instantAppSdk);
  }

  @NotNull
  @Override
  File getApk(@NotNull String arch, @NotNull String variant) throws ProvisionException {
    // The Supervisor apk present in the sdk is for architecture ARM v7. It will work in ARM v8
    return super.getApk(arch.compareTo(ARM_V8) == 0 ? ARM_V7 : arch, variant);
  }

  @Override
  void setFlags(@NotNull IDevice device, @NotNull String osBuildType) throws ProvisionException {
    if (osBuildType.compareTo(RELEASE_TYPE) == 0) {
      // We can't set any of these flags on a release-keys device, we rely on OTA updates for that
      return;
    }

    // Disable GPU Proxying only on emulators. Emulator GPU proxying is in the works. (b/34277156)
    boolean enableGpuProxying = osBuildType.compareTo(TEST_TYPE) != 0;
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e com.google.android.instantapps.common.enableGpuProxying " +
                        enableGpuProxying,
                        true /* root is required */);

    // Enable URL resolution
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e com.google.android.instantapps.disable_url_resolution false",
                        true /* root is required */);

    // Override minimum security patch level
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e com.google.android.instantapps.minimum_security_patch " +
                        "'2010-01-01'",
                        true /* root is required */);

    // Make sure that Phenotype uses Gservices flags
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e gms:phenotype:phenotype_flag:debug_bypass_phenotype true",
                        true /* root is required */);

    // Broadcast a Phenotype update
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.android.gms.phenotype.UPDATE",
                        true /* root is required */);

    // Make sure that if UrlHandler was disabled before it becomes enabled now
    executeShellCommand(device,
                        "CLASSPATH=/system/framework/pm.jar su root app_process " +
                        "/system/bin com.android.commands.pm.Pm enable " +
                        "com.google.android.instantapps.supervisor/.UrlHandler",
                        true /* root is required */);

    // Disable domain filter fetch on emulators when devman is present
    // TODO(b/34235489): When OnePlatform issue is resolved we could remove this
    if (osBuildType.compareTo(TEST_TYPE) == 0) {
      executeShellCommand(device,
                          "CLASSPATH=/system/framework/am.jar su root app_process " +
                          "/system/bin com.android.commands.am.Am broadcast " +
                          "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                          "-e gms:wh:disableDomainFilterFallback true",
                          true /* root is required */);
    }
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
}
