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
package com.android.tools.idea.sdkv2;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.welcome.install.Haxm;
import org.jetbrains.annotations.NotNull;

/**
 * Studio-specific SDK-related utilities
 */
public final class StudioSdkUtil {

  private static final ProgressIndicator LOGGER = new StudioLoggerProgressIndicator(StudioSdkUtil.class);

  /**
   * Find the best {@link PackageInstaller} for the given {@link RepoPackage}.
   */
  @NotNull
  public static PackageInstaller findBestInstaller(@NotNull RepoPackage p, @NotNull AndroidSdkHandler sdkHandler) {
    PackageInstaller installer = null;
    if (p instanceof RemotePackage) {
      LocalPackage local = sdkHandler.getLocalPackage(p.getPath(), LOGGER);
      if (local != null && ((RemotePackage)p).getArchive().getPatch(local.getVersion()) != null) {
        installer = new PatchInstaller();
      }
    }
    if (installer == null) {
      installer = new BasicInstaller();
    }

    if (p.getPath().equals(Haxm.REPO_PACKAGE_PATH)) {
      installer.registerStateChangeListener(new HaxmInstallListener());
    }
    if (p.getPath().equals(SdkConstants.FD_PLATFORM_TOOLS)) {
      installer.registerStateChangeListener(new PlatformToolsInstallListener(sdkHandler));
    }

    sdkHandler.registerInstallerListeners(installer, p);

    return installer;
  }

  private StudioSdkUtil() {}
}
