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
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.io.FileOp;
import com.android.repository.util.InstallerUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;

/**
 * Utilities for creating and installing binary diff packages.
 */
public class PatchInstallerUtil {

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
   * Gets the patcher jar required by our package.
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
    int maxVersion = 0;
    for (LocalPackage p : mgr.getPackages().getLocalPackagesForPrefix(PATCHER_PATH_PREFIX)) {
      try {
        int version = Integer.parseInt(p.getPath().substring(p.getPath().lastIndexOf('v') + 1));
        if (version > maxVersion) {
          patcher = p;
        }
      }
      catch (NumberFormatException ignored) {
      }
    }
    return patcher;
  }

  /**
   * Run the specified {@link PatchOperation}, applying the specified patch.
   */
  static boolean installPatch(@NotNull PatchOperation op,
                              @Nullable File patch,
                              @NotNull FileOp fop,
                              @NotNull ProgressIndicator progress) {
    if (patch == null) {
      return false;
    }
    PatchRunner patcher = PatchRunner.getPatchRunner(op.getPatcher(), progress, fop);
    if (patcher == null) {
      return false;
    }

    // The patcher won't expect this to be in the target directory, so delete it beforehand.
    fop.deleteFileOrFolder(new File(op.getLocation(progress), InstallerUtil.INSTALLER_DIR_FN));

    // Move the package.xml away, since the installer won't expect that either. But we want to be able to move it back if need be.
    File tempPath = patch.getParentFile();
    File existingPackageXml = new File(op.getLocation(progress), LocalRepoLoaderImpl.PACKAGE_XML_FN);
    File tempPackageXml = new File(tempPath, LocalRepoLoaderImpl.PACKAGE_XML_FN);
    fop.renameTo(existingPackageXml, tempPackageXml);

    boolean result;
    try {
      result = patcher.run(op.getLocation(progress), patch, progress);
    }
    catch (PatchRunner.RestartRequiredException e) {
      askAboutRestart(patcher, op, patch, fop, progress);
      result = false;
    }
    if (!result) {
      // We cancelled or selected restart later, or there was some problem. Move package.xml back into place.
      fop.renameTo(tempPackageXml, existingPackageXml);
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
                                      @NotNull final File patchFile,
                                      @NotNull FileOp fop,
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
        if (setupPatchDir(patchFile, patchRunner.getPatcherJar(), op.getPackage(), op.getRepoManager(), fop, progress)) {
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
  private static boolean setupPatchDir(@NotNull File patchFile, @NotNull File patcherFile, @NotNull RepoPackage toInstallOrDelete,
                                       @NotNull RepoManager mgr, @NotNull FileOp fop, @NotNull ProgressIndicator progress) {
    File patchesDir = new File(mgr.getLocalPath(), PATCHES_DIR_NAME);
    File patchDir;
    for (int i = 1; ; i++) {
      patchDir = new File(patchesDir, PATCH_DIR_PREFIX + i);
      if (!fop.exists(patchDir)) {
        fop.mkdirs(patchDir);
        break;
      }
    }
    try {
      File completePatch = new File(patchDir, PATCH_JAR_FN);
      fop.copyFile(patcherFile, completePatch);
      try (FileSystem completeFs = FileSystems.newFileSystem(URI.create("jar:" + completePatch.toURI()), new HashMap<>());
           FileSystem patchFs = FileSystems.newFileSystem(URI.create("jar:" + patchFile.toURI()), new HashMap<>())) {
        Files.copy(patchFs.getPath(PATCH_ZIP_FN), completeFs.getPath(PATCH_ZIP_FN));
      }
      InstallerUtil.writePendingPackageXml(toInstallOrDelete, patchDir, mgr, fop, progress);
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
  public static File generatePatch(PatchOperation patchOp, File destDir, FileOp fop, ProgressIndicator progress) {
    PatchRunner runner = PatchRunner.getPatchRunner(patchOp.getPatcher(), progress, fop);
    if (runner == null) {
      return null;
    }
    LocalPackage existing = patchOp.getExisting();
    File existingRoot = existing == null ? null : existing.getLocation();
    String existingDescription = existing == null ? "None" : existing.getDisplayName() + " Version " + existing.getVersion();
    String description = patchOp.getNewVersionName();
    File destination = new File(destDir, PATCH_JAR_FN);
    File newFilesRoot = patchOp.getNewFilesRoot();
    if (runner.generatePatch(existingRoot, newFilesRoot, existingDescription, description, destination, progress)) {
      return destination;
    }
    return null;
  }
}
