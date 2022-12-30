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

import com.android.io.CancellableFileIo;
import com.android.repository.api.Dependency;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.utils.FileUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for creating and installing binary diff packages.
 */
public final class PatchInstallerUtil {

  /**
   * Repo-style path prefix for patcher packages.
   */
  public static final String PATCHER_PATH_PREFIX = "patcher";

  /**
   * Directory under the sdk root where we keep pending patches that may require restart.
   */
  static final String PATCHES_DIR_NAME = ".patches";

  /**
   * Filename of the patch jar itself.
   */
  static final String PATCH_JAR_FN = "patch.jar";

  /**
   * Prefix of dirs containing individual patches.
   */
  static final String PATCH_DIR_PREFIX = PatchInstallerFactory.class.getSimpleName();

  /**
   * The actual patch file itself, inside the patch jar.
   */
  private static final String PATCH_ZIP_FN = "patch-file.zip";

  /**
   * Gets the installed patcher package required by our package.
   *
   * @return The patcher package that the given package depends on, or null if none was found.
   */
  @Nullable
  static LocalPackage getDependantPatcher(@NotNull RemotePackage remote, @NotNull RepoManager mgr) {
    for (Dependency d : remote.getAllDependencies()) {
      if (d.getPath().startsWith(PATCHER_PATH_PREFIX + RepoPackage.PATH_SEPARATOR)) {
        LocalPackage patcher = mgr.getPackages().getLocalPackages().get(d.getPath());
        if (patcher != null) {
          return patcher;
        }
      }
    }
    return null;
  }

  /**
   * Gets the {@link LocalPackage} for the latest patcher we have installed.
   */
  @Nullable
  static LocalPackage getLatestPatcher(@NotNull RepoManager mgr) {
    LocalPackage patcher = null;
    for (LocalPackage p : mgr.getPackages().getLocalPackagesForPrefix(PATCHER_PATH_PREFIX)) {
      if (patcher == null || comparePatcherPaths(p.getPath(), patcher.getPath()) > 0) {
        patcher = p;
      }
    }
    return patcher;
  }

  static int comparePatcherPaths(@NotNull String path1, @NotNull String path2) {
    int v1 = -1;
    int v2 = -1;
    try {
      v1 = Integer.parseInt(path1.substring(path1.lastIndexOf('v') + 1));
    }
    catch (NumberFormatException ignored) {
    }
    try {
      v2 = Integer.parseInt(path2.substring(path2.lastIndexOf('v') + 1));
    }
    catch (NumberFormatException ignored) {
    }
    return Integer.compare(v1, v2);
  }

  /**
   * Run the specified {@link PatchOperation}, applying the specified patch.
   */
  static boolean installPatch(@NotNull PatchOperation op,
                              @Nullable Path patch,
                              @NotNull ProgressIndicator progress) {
    if (patch == null) {
      return false;
    }
    LocalPackage patcherPackage = op.getPatcher(progress);
    if (patcherPackage == null) {
      return false;
    }
    PatchRunner patcher = new PatchRunner.DefaultFactory().getPatchRunner(patcherPackage, progress);
    if (patcher == null) {
      return false;
    }

    // The patcher won't expect this to be in the target directory, so delete it beforehand.
    FileOpUtils.deleteFileOrFolder(op.getLocation(progress).resolve(InstallerUtil.INSTALLER_DIR_FN));

    // Move the package.xml away, since the installer won't expect that either. But we want to be able to move it back if need be.
    Path tempPath = patch.getParent();
    Path existingPackageXml = op.getLocation(progress).resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN);
    Path tempPackageXml = tempPath.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN);
    try {
      Files.move(existingPackageXml, tempPackageXml);
    }
    catch (IOException e) {
      return false;
    }

    boolean result;
    try {
      result = patcher.run(op.getLocation(progress), patch, progress);
    }
    catch (PatchRunner.RestartRequiredException e) {
      askAboutRestart(patcher, op, patch, progress);
      result = false;
    }
    if (!result) {
      // We cancelled or selected restart later, or there was some problem. Move package.xml back into place.
      try {
        Files.move(tempPackageXml, existingPackageXml);
      }
      catch (IOException ignore) {
        // ignore, we've done our best
      }
      return false;
    }
    progress.logInfo("Done");

    return true;
  }

  /**
   * If a patch fails to install because Studio is locking some of the files, we have to restart studio. Ask if the user wants
   * to, and then move things into place so they can be picked up on restart.
   */
  private static void askAboutRestart(@NotNull PatchRunner patchRunner,
                                      @NotNull PatchOperation op,
                                      @NotNull final Path patchFile,
                                      @NotNull final ProgressIndicator progress) {
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    application.invokeLater(() -> {
      String[] options;
      ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();
      boolean restartable = application.isRestartCapable();
      if (restartable) {
        options = new String[]{"Cancel", "Restart Later", "Restart Now"};
      }
      else {
        options = new String[]{"Cancel", String.format("Exit %s", names.getProductName())};
      }
      String message;
      if (op.getExisting() != null) {
        message = String.format(
          "%1$s is currently in use by %2$s and cannot be updated. Please restart to complete installation.",
          op.getExisting().getDisplayName(),
          names.getFullProductName());
      }
      else {
        message = String.format(
          "Some files in the destination are currently in use by %1$s. Please restart to complete installation.",
          names.getFullProductName());
      }
      int result = Messages.showDialog(
        (Project)null, message,
        "Restart Required", options, options.length - 1, AllIcons.General.QuestionDialog);
      if (result == 0) {
        progress.logInfo("Cancelled");
      }
      else {
        if (setupPatchDir(patchFile, patchRunner.getPatcherJar(), op.getPackage(), op.getRepoManager(), progress)) {
          if (result == 1 && restartable) {
            progress.logInfo("Installation will continue after restart");
          }
          else {
            application.exit(true, true);
          }
        }
      }
    }, ModalityState.any());
  }

  /**
   * Create and populate the directory that we'll look in during startup for pending patches.
   * This includes copying the patch zip there, and then adding the patcher jar into the zip (so it can be run by the update runner).
   */
  @SuppressWarnings("NoNioFilesCopy") // Use of ZipPath as source is okay since there is no read-only bit
  private static boolean setupPatchDir(@NotNull Path patchFile, @NotNull Path patcherFile, @NotNull RepoPackage toInstallOrDelete,
                                       @NotNull RepoManager mgr, @NotNull ProgressIndicator progress) {
    Path localPath = mgr.getLocalPath();
    Path patchesDir = (localPath == null ? patchFile.getFileSystem().getPath("") : localPath).resolve(PATCHES_DIR_NAME);
    Path patchDir;
    try {
      for (int i = 1; ; i++) {
        patchDir = patchesDir.resolve(PATCH_DIR_PREFIX + i);
        if (!CancellableFileIo.exists(patchDir)) {
          Files.createDirectories(patchDir);
          break;
        }
      }
      Path completePatch = patchDir.resolve(PATCH_JAR_FN);
      FileUtils.copyFile(patcherFile, completePatch);
      try (FileSystem completeFs = FileSystems.newFileSystem(URI.create("jar:" + completePatch.toUri()), new HashMap<>());
           FileSystem patchFs = FileSystems.newFileSystem(URI.create("jar:" + patchFile.toUri()), new HashMap<>())) {
        Files.copy(patchFs.getPath(PATCH_ZIP_FN), completeFs.getPath(PATCH_ZIP_FN));
      }
      InstallerUtil.writePendingPackageXml(toInstallOrDelete, patchDir, mgr, progress);
    }
    catch (IOException e) {
      progress.logWarning("Error while setting up patch.", e);
      return false;
    }
    return true;
  }

  /**
   * Generates the patch file corresponding to the specified {@link PatchOperation}.
   *
   * @param patchOp The operation specifying the "before" and "after" directories for the patch, as well as other needed information.
   * @param destDir The directory in which to generate the patch.
   * @return A handle to the generated patch, or {@code null} if there was a problem.
   */
  public static Path generatePatch(PatchOperation patchOp, Path destDir, ProgressIndicator progress) {
    LocalPackage patcher = patchOp.getPatcher(progress.createSubProgress(0.1));
    progress.setFraction(0.1);
    if (patcher == null) {
      return null;
    }
    PatchRunner runner = new PatchRunner.DefaultFactory().getPatchRunner(patcher, progress);
    if (runner == null) {
      return null;
    }
    LocalPackage existing = patchOp.getExisting();
    Path existingRoot = existing == null ? null : existing.getLocation();
    String existingDescription = existing == null ? "None" : existing.getDisplayName() + " Version " + existing.getVersion();
    String description = patchOp.getNewVersionName();
    Path destination = destDir.resolve(PATCH_JAR_FN);
    File newFilesRoot = patchOp.getNewFilesRoot().toFile();
    if (runner.generatePatch(existingRoot == null ? null : existingRoot.toFile(), newFilesRoot, existingDescription, description,
                             destination.toFile(), progress.createSubProgress(1))) {
      progress.setFraction(1);
      return destination;
    }
    progress.setFraction(1);
    return null;
  }

  /**
   * If a patcher is being installed at the same time as a patch, we need to make sure the patcher install completes before trying to
   * apply the patch.
   *
   * @param remote The patch we're trying to apply
   * @return The in-progress install operation, or {@code null} if there is none.
   */
  @Nullable
  public static PackageOperation getInProgressDependantPatcherInstall(@NotNull RemotePackage remote, @NotNull RepoManager mgr) {
    Map<String, RemotePackage> remotePackages = mgr.getPackages().getRemotePackages();
    for (Dependency dependency : remote.getAllDependencies()) {
      if (dependency.getPath().startsWith(PATCHER_PATH_PREFIX + RepoPackage.PATH_SEPARATOR)) {
        RemotePackage remotePatcher = remotePackages.get(dependency.getPath());
        if (remotePatcher != null) {
          PackageOperation inProgress = mgr.getInProgressInstallOperation(remotePatcher);
          if (inProgress != null && inProgress.getInstallStatus() != PackageOperation.InstallStatus.FAILED) {
            return inProgress;
          }
        }
      }
    }
    return null;
  }
}
