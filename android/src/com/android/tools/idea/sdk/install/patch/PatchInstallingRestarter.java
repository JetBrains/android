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
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.installer.AbstractInstaller;
import com.android.repository.impl.installer.AbstractInstallerFactory;
import com.android.repository.impl.installer.AbstractUninstaller;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.install.StudioSdkInstallListenerFactory;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.idea.Main;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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
  private final FileOp myFileOp;

  public PatchInstallingRestarter(@NotNull AndroidSdkHandler sdkHandler, @NotNull FileOp fileOp) {
    mySdkHandler = sdkHandler;
    myFileOp = fileOp;
  }

  /**
   * Find any pending patches under the given sdk root that require studio to be restarted, and if there are any, restart and install them.
   * If they have already been installed (as indicated by the patch itself being missing, and the revision mentioned in source.properties
   * matching that in the pending XML, do the install complete actions (write package.xml and fire listeners) and clean up.
   */
  public void restartAndInstallIfNecessary() {
    File patchesDir = new File(mySdkHandler.getLocation(), PatchInstallerUtil.PATCHES_DIR_NAME);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    if (patchesDir.exists()) {
      File[] subDirs = patchesDir.listFiles(file -> file.isDirectory() && file.getName().startsWith(PatchInstallerUtil.PATCH_DIR_PREFIX));
      for (File patchDir : subDirs) {
        processPatch(mySdkHandler.getLocation(), progress, patchDir);
      }
    }
  }

  /**
   * Either restart and install the given patch or delete it (if it's already installed).
   */
  private void processPatch(File androidSdkPath, StudioLoggerProgressIndicator progress, File patchDir) {
    RepoPackage pendingPackage = null;
    File installDir = null;
    try {
      RepoManager mgr = mySdkHandler.getSdkManager(progress);
      Repository repo = InstallerUtil.readPendingPackageXml(patchDir, mgr, myFileOp, progress);
      if (repo != null) {
        File patch = new File(patchDir, PatchInstallerUtil.PATCH_JAR_FN);
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
        File existingPackageXml = new File(installDir, LocalRepoLoaderImpl.PACKAGE_XML_FN);
        File oldPackageXml = new File(patchDir, OLD_PACKAGE_XML_FN);
        if (patch.exists() && existingPackageXml.renameTo(oldPackageXml)) {
          // This will exit the app.
          Main.installPatch("sdk", PatchInstallerUtil.PATCH_JAR_FN, FileUtil.getTempDirectory(), patch, installDir.getAbsolutePath());
        }
        else {
          // The patch is already installed, or was cancelled.

          String relativePath = FileOpUtils.makeRelative(androidSdkPath, installDir, myFileOp);
          // Use the old mechanism to get the version, since it's actually part of the package itself. Thus we can tell if the patch
          // has already been applied.
          Revision rev = AndroidCommonUtils.parsePackageRevision(androidSdkPath.getPath(), relativePath);
          if (rev != null && rev.equals(pendingPackage.getVersion())) {
            // We need to make sure the listeners are fired, so create an installer that does nothing and invoke it.
            InstallerFactory dummyFactory = new DummyInstallerFactory();
            dummyFactory.setListenerFactory(new StudioSdkInstallListenerFactory(mySdkHandler));
            if (remote) {
              Installer installer = dummyFactory.createInstaller((RemotePackage)pendingPackage, mgr, new StudioDownloader(), myFileOp);
              installer.complete(progress);
            }
            else {
              Uninstaller uninstaller = dummyFactory.createUninstaller((LocalPackage)pendingPackage, mgr, myFileOp);
              uninstaller.complete(progress);
            }
          }
          else {
            // something went wrong. Move the old package.xml back into place.
            progress.logWarning("Failed to find version information in " + new File(androidSdkPath, SdkConstants.FN_SOURCE_PROP));
            oldPackageXml.renameTo(existingPackageXml);
          }
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
      myFileOp.deleteFileOrFolder(patchDir);
    }
    catch (Exception e) {
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
                                          @NotNull Downloader downloader,
                                          @NotNull FileOp fop) {
      return new AbstractInstaller(p, mgr, downloader, fop) {
        @Override
        protected boolean doComplete(@Nullable File installTemp,
                                     @NotNull ProgressIndicator progress) {
          return true;
        }

        @Override
        protected boolean doPrepare(@NotNull File installTempPath,
                                    @NotNull ProgressIndicator progress) {
          return false;
        }
      };
    }

    @NotNull
    @Override
    protected Uninstaller doCreateUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
      return new AbstractUninstaller(p, mgr, fop) {
        @Override
        protected boolean doPrepare(@Nullable File installTemp,
                                    @NonNull ProgressIndicator progress) {
          return false;
        }

        @Override
        protected boolean doComplete(@Nullable File installTemp,
                                     @NonNull ProgressIndicator progress) {
          return true;
        }
      };
    }
  }
}
