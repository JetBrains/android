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

import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.AbstractInstallerFactory;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.FileUtilKt;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * The first version of the patcher that actually works reliably.
   */
  private static final String KNOWN_GOOD_VERSION = PatchInstallerUtil.PATCHER_PATH_PREFIX + RepoPackage.PATH_SEPARATOR + "v4";
  // Don't pseudo-patch components larger than 100M
  private static final long PSEUDO_PATCH_CUTOFF = 1024 * 1024 * 100;

  private final PatchRunner.Factory myPatchRunnerFactory;

  public PatchInstallerFactory() {
    this(new PatchRunner.DefaultFactory());
  }

  @VisibleForTesting
  PatchInstallerFactory(@NotNull PatchRunner.Factory runnerFactory) {
    myPatchRunnerFactory = runnerFactory;
  }

  @NotNull
  @Override
  protected Installer doCreateInstaller(@NotNull RemotePackage remote,
                                        @NotNull RepoManager mgr,
                                        @NotNull Downloader downloader) {
    LocalPackage local = mgr.getPackages().getLocalPackages().get(remote.getPath());
    if (hasPatch(local, remote)) {
      return new PatchInstaller(local, remote, downloader, mgr);
    }
    return new FullInstaller(local, remote, mgr, downloader);
  }

  private static boolean hasPatch(@Nullable LocalPackage local, @NotNull RemotePackage remote) {
    Archive archive = remote.getArchive();
    assert archive != null;
    return local != null && archive.getPatch(local.getVersion()) != null;
  }

  @NotNull
  @Override
  protected Uninstaller doCreateUninstaller(@NotNull LocalPackage local, @NotNull RepoManager mgr) {
    return new PatchUninstaller(local, mgr);
  }

  /**
   * Check whether we can create an installer for this package.
   */
  @Override
  protected boolean canHandlePackage(@NotNull RepoPackage p, @NotNull RepoManager manager) {
    ProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    if (p instanceof LocalPackage) {
      // Uninstall case. Only useful on windows, since it locks in-use files.
      if (FileOpUtils.isWindows()) {
        try {
          if (FileUtilKt.recursiveSize(((LocalPackage)p).getLocation()) >= PSEUDO_PATCH_CUTOFF) {
            // Don't pseudo-patch if the file is too big.
            return false;
          }
        }
        catch (IOException e) {
          // ignore
        }
        // Any patcher will do: just see if we have any patcher available.
        LocalPackage latestPatcher = PatchInstallerUtil.getLatestPatcher(manager);
        // don't try to use the patcher to uninstall itself
        return latestPatcher != null && !latestPatcher.equals(p);
      }
      else {
        // Don't use patcher on non-windows.
        return false;
      }
    }

    LocalPackage local = manager.getPackages().getLocalPackages().get(p.getPath());
    RemotePackage remote = (RemotePackage)p;
    if (local == null || (!FileOpUtils.isWindows() && !hasPatch(local, remote))) {
      // If this isn't an update, or if we're not on windows and there's no patch, there's no reason to use the patcher.
      return false;
    }

    if (hasPatch(local, remote)) {
      // If a patch is available, make sure we can get the patcher itself
      LocalPackage patcher = PatchInstallerUtil.getDependantPatcher((RemotePackage)p, manager);
      if (patcher != null && myPatchRunnerFactory.getPatchRunner(patcher, progress) != null) {
        return true;
      }

      // Maybe it's not installed yet, but is being installed right now as part of the same operation.
      if (PatchInstallerUtil.getInProgressDependantPatcherInstall((RemotePackage)p, manager) != null) {
        return true;
      }

      // We don't have the right patcher. Give up unless we're on Windows.
      if (!FileOpUtils.isWindows()) {
        return false;
      }
    }

    // At this point we must be on Windows.
    if (((RemotePackage)p).getArchive().getComplete().getSize() >= PSEUDO_PATCH_CUTOFF) {
      // Don't pseudo-patch if the file is too big.
      return false;
    }
    // There's no patch available, but if a patch installer is installed and better than KNOWN_GOOD_VERSION we can still use it.
    LocalPackage patcher = PatchInstallerUtil.getLatestPatcher(manager);
    return patcher != null && PatchInstallerUtil.comparePatcherPaths(patcher.getPath(), KNOWN_GOOD_VERSION) >= 0;
  }
}
