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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.io.CancellableFileIo;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.Repository;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.AbstractInstaller;
import com.android.repository.impl.installer.AbstractInstallerFactory;
import com.android.repository.impl.installer.AbstractUninstaller;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.install.StudioSdkInstallListenerFactory;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.utils.PathUtils;
import com.intellij.openapi.ui.Messages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facility for processing SDK patches that require a restart of Studio.
 */
public class PatchInstallingRestarter {
  /**
   * Temporary path for the package.xml during install, so the patcher doesn't complain about it being there. It will be moved back into
   * place if there's a problem.
   */
  private static final String OLD_PACKAGE_XML_FN = "package.xml.old";

  private final AndroidSdkHandler mySdkHandler;

  public PatchInstallingRestarter(@NotNull AndroidSdkHandler sdkHandler) {
    mySdkHandler = sdkHandler;
  }

  /**
   * Find any pending patches under the given sdk root that require studio to be restarted, and if there are any, restart and install them.
   * If they have already been installed (as indicated by the patch itself being missing, and the revision mentioned in source.properties
   * matching that in the pending XML, do the install complete actions (write package.xml and fire listeners) and clean up.
   */
  public void restartAndInstallIfNecessary() {
    Path sdkLocation = mySdkHandler.getLocation();
    if (sdkLocation == null) {
      return;
    }
    Path patchesDir = sdkLocation.resolve(PatchInstallerUtil.PATCHES_DIR_NAME);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    try (Stream<Path> subDirs = CancellableFileIo.list(patchesDir)) {
      subDirs.filter(file -> CancellableFileIo.isDirectory(file) && file.getFileName().toString().startsWith(PatchInstallerUtil.PATCH_DIR_PREFIX))
        .forEach(patchDir -> processPatch(sdkLocation, progress, patchDir));
    }
    catch (IOException e) {
      // We couldn't process patches, so just go ahead with startup.
    }
  }

  /**
   * Either restart and install the given patch or delete it (if it's already installed).
   */
  private void processPatch(Path androidSdkPath, StudioLoggerProgressIndicator progress, Path patchDir) {
    RepoPackage pendingPackage = null;
    Path installDir = null;
    try {
      RepoManager mgr = mySdkHandler.getSdkManager(progress);
      Repository repo = InstallerUtil.readPendingPackageXml(patchDir, mgr, progress);
      if (repo != null) {
        Path patch = patchDir.resolve(PatchInstallerUtil.PATCH_JAR_FN);
        pendingPackage = repo.getLocalPackage();
        boolean remote = false;
        if (pendingPackage != null) {
          // If the pending package was local, use the corresponding installed local package.
          installDir = mgr.getPackages().getLocalPackages().get(pendingPackage.getPath()).getLocation();
        }
        else {
          // Otherwise it's remote.
          pendingPackage = repo.getRemotePackage().get(0);
          installDir = ((RemotePackage)pendingPackage).getInstallDir(mgr, progress);
          remote = true;
        }
        Path existingPackageXml = installDir.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN);
        Path oldPackageXml = patchDir.resolve(OLD_PACKAGE_XML_FN);
        if (CancellableFileIo.exists(patch)) {
          try {
            Files.move(existingPackageXml, oldPackageXml);
            // This will exit the app.
            //Main.installPatch("sdk", PatchInstallerUtil.PATCH_JAR_FN, FileUtil.getTempDirectory(), patch, installDir.getAbsolutePath(), Main.PATCHER_MAIN);
            //return;
            throw new UnsupportedOperationException("TODO: Merge");
          }
          catch (IOException ignore) {
            // fall through to the logic below
          }
        }
        // The patch is already installed, or was cancelled.

        String relativePath = androidSdkPath.relativize(installDir).toString();
        // Use the old mechanism to get the version, since it's actually part of the package itself. Thus we can tell if the patch
        // has already been applied.
        Revision rev = AndroidBuildCommonUtils.parsePackageRevision(androidSdkPath.toString(), relativePath);
        if (rev != null && rev.equals(pendingPackage.getVersion())) {
          // We need to make sure the listeners are fired, so create an installer that does nothing and invoke it.
          InstallerFactory dummyFactory = new DummyInstallerFactory();
          dummyFactory.setListenerFactory(new StudioSdkInstallListenerFactory(mySdkHandler));
          if (remote) {
            Installer installer = dummyFactory.createInstaller((RemotePackage)pendingPackage, mgr, new StudioDownloader());
            installer.complete(progress);
          }
          else {
            Uninstaller uninstaller = dummyFactory.createUninstaller((LocalPackage)pendingPackage, mgr);
            uninstaller.complete(progress);
          }
        }
        else {
          // something went wrong. Move the old package.xml back into place.
          progress.logWarning("Failed to find version information in " + androidSdkPath.resolve(SdkConstants.FN_SOURCE_PROP));
          Files.move(oldPackageXml, existingPackageXml);
        }
      }
    }
    catch (Exception e) {
      StringBuilder message = new StringBuilder("A problem occurred while installing ");
      message.append(pendingPackage != null ? pendingPackage.getDisplayName() : "an SDK package");
      if (installDir != null) {
        message.append(" in ").append(installDir);
      }
      message.append(". Please try again.");
      Messages.showErrorDialog(message.toString(), "Error Launching SDK Component Installer");
      progress.logWarning("Failed to install SDK package", e);
    }

    // If we get here we either got an error or the patch was already installed. Delete the patch dir.
    try {
      PathUtils.deleteRecursivelyIfExists(patchDir);
    }
    catch (IOException e) {
      progress.logWarning("Problem during patch cleanup", e);
    }
  }

  /**
   * Once a restart has happened, we need to fire the appropriate listeners to do any finishing work. This factory creates
   * installers that do nothing, but will allow the relevant listeners to be called.
   */
  private static class DummyInstallerFactory extends AbstractInstallerFactory {

    @NotNull
    @Override
    protected Installer doCreateInstaller(@NotNull RemotePackage p,
                                          @NotNull RepoManager mgr,
                                          @NotNull Downloader downloader) {
      return new AbstractInstaller(p, mgr, downloader) {
        @Override
        protected boolean doComplete(@Nullable Path installTemp,
                                     @NotNull ProgressIndicator progress) {
          return true;
        }

        @Override
        protected boolean doPrepare(@NotNull Path installTempPath,
                                    @NotNull ProgressIndicator progress) {
          return false;
        }
      };
    }

    @NotNull
    @Override
    protected Uninstaller doCreateUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr) {
      return new AbstractUninstaller(p, mgr) {
        @Override
        protected boolean doPrepare(@Nullable Path installTemp,
                                    @NonNull ProgressIndicator progress) {
          return false;
        }

        @Override
        protected boolean doComplete(@Nullable Path installTemp,
                                     @NonNull ProgressIndicator progress) {
          return true;
        }
      };
    }
  }
}
