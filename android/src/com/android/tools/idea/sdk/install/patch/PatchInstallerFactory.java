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
package com.android.tools.idea.sdk.install.patch;

import com.android.repository.api.*;
import com.android.repository.impl.installer.AbstractInstallerFactory;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for installers/uninstallers that use the IntelliJ Updater mechanism to update the SDK.
 *
 * The actual logic for applying the diffs is not here; rather, it is contained in a separate SDK component. This is to allow
 * changes in the diff algorithm or patch format without backward compatibility concerns.
 * Each SDK package that includes diff-type archives must also include a dependency on a patcher component. That component contains
 * the necessary code to apply the diff (this is the same as the code used to update studio itself), as well as UI integration between
 * the patcher and this installer.
 */
public class PatchInstallerFactory extends AbstractInstallerFactory {

  @NotNull
  @Override
  protected Installer doCreateInstaller(@NotNull RemotePackage p,
                                        @NotNull RepoManager mgr,
                                        @NotNull Downloader downloader,
                                        @NotNull FileOp fop) {
    LocalPackage local = mgr.getPackages().getLocalPackages().get(p.getPath());
    Archive archive = p.getArchive();
    assert archive != null;
    if (local != null && archive.getPatch(local.getVersion()) != null) {
      return new PatchInstaller(local, p, downloader, mgr, fop);
    }
    return new FullInstaller(local, p, mgr, downloader, fop);
  }

  @NotNull
  @Override
  protected Uninstaller doCreateUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
    return new PatchUninstaller(p, mgr, fop);
  }

  /**
   * @return {@code true} if some type of patch installer can install/uninstall the given package.
   */
  public static boolean canHandlePackage(@NotNull RepoPackage p, @NotNull AndroidSdkHandler handler) {
    ProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    RepoManager mgr = handler.getSdkManager(progress);
    if (p instanceof LocalPackage) {
      // Uninstall case: just see if we have any patcher available.
      return PatchInstallerUtil.getLatestPatcher(mgr) != null;
    }
    LocalPackage patcher = PatchInstallerUtil.getDependantPatcher((RemotePackage)p, mgr);
    return patcher != null && PatchRunner.getPatchRunner(patcher, progress, handler.getFileOp()) != null;
  }
}
