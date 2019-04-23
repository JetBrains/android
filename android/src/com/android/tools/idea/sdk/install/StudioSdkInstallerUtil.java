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

import com.google.common.annotations.VisibleForTesting;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.install.patch.PatchInstallerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Studio-specific SDK-related utilities
 */
public final class StudioSdkInstallerUtil {

  private final SettingsController mySettingsController;

  /**
   * Find the best {@link InstallerFactory} for the given {@link RepoPackage}.
   */
  @NotNull
  public static InstallerFactory createInstallerFactory(@NotNull AndroidSdkHandler sdkHandler) {
    return new StudioSdkInstallerUtil().doCreateInstallerFactory(sdkHandler);
  }

  @VisibleForTesting
  @NotNull
  InstallerFactory doCreateInstallerFactory(@NotNull AndroidSdkHandler sdkHandler) {
    InstallerFactory factory;
    InstallerFactory basicFactory = new BasicInstallerFactory();
    if (mySettingsController.getDisableSdkPatches()) {
      factory = basicFactory;
    }
    else {
      factory = new PatchInstallerFactory();
      factory.setFallbackFactory(basicFactory);
    }
    factory.setListenerFactory(new StudioSdkInstallListenerFactory(sdkHandler));
    return factory;
  }

  private StudioSdkInstallerUtil() {
    this(StudioSettingsController.getInstance());
  }

  @VisibleForTesting
  StudioSdkInstallerUtil(@NotNull SettingsController settingsController) {
    mySettingsController = settingsController;
  }
}
