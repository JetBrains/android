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

import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.meta.Archive;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * Studio-specific SDK-related utilities
 */
public final class StudioSdkInstallerUtil {

  private static final ProgressIndicator LOGGER = new StudioLoggerProgressIndicator(StudioSdkInstallerUtil.class);

  /**
   * Find the best {@link InstallerFactory} for the given {@link RepoPackage}.
   */
  @NotNull
  public static InstallerFactory createInstallerFactory(@NotNull RepoPackage p, @NotNull AndroidSdkHandler sdkHandler) {
    InstallerFactory factory = null;
    if (p instanceof RemotePackage) {
      LocalPackage local = sdkHandler.getLocalPackage(p.getPath(), LOGGER);
      Archive archive = ((RemotePackage)p).getArchive();
      assert archive != null;
      if (local != null && archive.getPatch(local.getVersion()) != null) {
        factory = new PatchInstallerFactory();
      }
    }
    if (factory == null) {
      factory = new BasicInstallerFactory();
    }
    factory.setListenerFactory(new StudioSdkInstallListenerFactory(sdkHandler));
    return factory;
  }

  private StudioSdkInstallerUtil() {}
}
