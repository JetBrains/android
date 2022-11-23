/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.install;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.*;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.adb.AdbService;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Installer for platform-tools that stops ADB before installing or uninstalling.
 */
public class PlatformToolsInstallListener implements PackageOperation.StatusChangeListener {
  private final AndroidSdkHandler mySdkHandler;

  public PlatformToolsInstallListener(AndroidSdkHandler sdkHandler) {
    mySdkHandler = sdkHandler;
  }

  private void stopAdb(@NonNull ProgressIndicator progress) {
    AdbService adbService = AdbService.getInstance();
    progress.logInfo("Stopping ADB...");
    Path adb = getAdb(progress);
    if (adb != null) {
      try {
        // We have to actually initialize the service, since there might be adb processes left over from before this run of studio.
        adbService.getDebugBridge(adb.toFile()).get();
      }
      catch (Exception e) {
        progress.logWarning("Failed to get ADB instance", e);
      }
    }
    try {
      adbService.terminateDdmlib();
    }
    catch (TimeoutException e) {
      progress.logWarning("Failed to terminate running ADB instance", e);
    }
  }

  @Nullable
  private Path getAdb(@NotNull ProgressIndicator progress) {
    LocalPackage localPackage = mySdkHandler.getLocalPackage(SdkConstants.FD_PLATFORM_TOOLS, progress);
    if (localPackage != null) {
      return localPackage.getLocation().resolve(SdkConstants.FN_ADB);
    }
    return null;
  }

  @Override
  public void statusChanged(@NonNull PackageOperation installer, @NonNull final ProgressIndicator progress) {
    if (installer.getInstallStatus() == PackageOperation.InstallStatus.RUNNING) {
      stopAdb(progress);
    }
  }
}
