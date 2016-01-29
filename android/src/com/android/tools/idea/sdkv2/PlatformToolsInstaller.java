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
package com.android.tools.idea.sdkv2;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.io.FileOp;
import com.android.tools.idea.ddms.adb.AdbService;

import java.io.File;
import java.util.Map;

/**
 * Installer for platform-tools that stops ADB before installing or uninstalling.
 */
public class PlatformToolsInstaller extends BasicInstaller {
  @Override
  public boolean uninstall(@NonNull LocalPackage p,
                           @NonNull ProgressIndicator progress,
                           @NonNull RepoManager manager,
                           @NonNull FileOp fop) {
    stopAdb(progress, manager);
    return super.uninstall(p, progress, manager, fop);
  }

  @Override
  public boolean install(@NonNull RemotePackage p,
                         @NonNull Downloader downloader,
                         @Nullable SettingsController settings,
                         @NonNull ProgressIndicator progress,
                         @NonNull RepoManager manager,
                         @NonNull FileOp fop) {
    stopAdb(progress, manager);
    return super.install(p, downloader, settings, progress, manager, fop);
  }

  private static void stopAdb(@NonNull ProgressIndicator progress, @NonNull RepoManager manager) {
    AdbService adbService = AdbService.getInstance();
    progress.logInfo("Stopping ADB...");
    File adb = getAdb(manager);
    if (adb != null) {
      try {
        // We have to actually initialize the service, since there might be adb processes left over from before this run of studio.
        adbService.getDebugBridge(adb).get();
      }
      catch (Exception e) {
        progress.logWarning("Failed to get ADB instance", e);
      }
    }
    adbService.terminateDdmlib();
  }

  @Nullable
  private static File getAdb(@NonNull RepoManager manager) {
    Map<String, ? extends LocalPackage> localPackages = manager.getPackages().getLocalPackages();
    LocalPackage localPackage = localPackages.get(SdkConstants.FD_PLATFORM_TOOLS);
    if (localPackage != null) {
      return new File(localPackage.getLocation().getPath(), SdkConstants.FN_ADB);
    }
    return null;
  }

}
