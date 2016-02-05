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

import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstaller;
import com.android.repository.impl.manager.LocalRepoLoader;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Installer that applies binary diffs.
 * The actual logic for applying the diffs is not here; rather, it is contained in a separate SDK component. This is to allow
 * changes in the diff algorithm or patch format without backward compatibility concerns.
 * Each SDK package that includes diff-type archives must also include a dependency on a patcher component. That component contains
 * the necessary code to apply the diff (this is the same as the code used to update studio itself), as well as UI integration between
 * the patcher and this installer.
 */
public class PatchInstaller extends BasicInstaller {

  /**
   * Repo-style path prefix (including separator) for patcher packages.
   */
  public static final String PATCHER_PATH_PREFIX = "patcher" + RepoPackage.PATH_SEPARATOR;

  /**
   * Name of the patcher jar file from the patcher package.
   */
  private static final String PATCHER_JAR_FN = "patcher.jar";

  /**
   * Runner class we'll invoke from the jar.
   */
  private static final String RUNNER_CLASS_NAME = "com.intellij.updater.Runner";

  /**
   * Interface from the patcher jar for the UI. Needed to get the update method.
   */
  private static final String UPDATER_UI_CLASS_NAME = "com.intellij.updater.UpdaterUI";

  /**
   * Interface for the actual UI class we'll create.
   */
  private static final String REPO_UI_CLASS_NAME = "com.android.tools.idea.updaterui.RepoUpdaterUI";

  /**
   * Cache of patcher classes. Key is jar file, subkey is class name.
   */
  private static Map<File, Map<String, Class<?>>> ourPatcherCache = new WeakHashMap<File, Map<String, Class<?>>>();

  /**
   * {@inheritDoc}
   *
   * If the patch fails, fall back to trying a complete install.
   */
  @Override
  public boolean install(@NotNull RemotePackage p,
                         @NotNull Downloader downloader,
                         @Nullable SettingsController settings,
                         @NotNull ProgressIndicator progress,
                         @NotNull RepoManager manager,
                         @NotNull FileOp fop) {
    if (!doInstall(p, downloader, settings, progress, manager, fop)) {
      progress.logWarning("Failed to install patch, attempting fresh install...");
      return super.install(p, downloader, settings, progress, manager, fop);
    }
    return true;
  }

  /**
   * Actually do the patch install.
   */
  private boolean doInstall(@NotNull RemotePackage p,
                            @NotNull Downloader downloader,
                            @Nullable SettingsController settings,
                            @NotNull ProgressIndicator progress,
                            @NotNull RepoManager manager,
                            @NotNull FileOp fop) {
    Map<String, ? extends LocalPackage> localPackages = manager.getPackages().getLocalPackages();
    LocalPackage local = localPackages.get(p.getPath());
    assert local != null;
    Archive archive = p.getArchive();
    assert archive != null;
    Archive.PatchType patch = archive.getPatch(local.getVersion());
    assert patch != null;

    File patcherJar = getPatcherFile(p, localPackages, progress, fop);
    if (patcherJar == null) {
      progress.logWarning("Couldn't find patcher jar!");
      return false;
    }

    Map<String, Class<?>> classMap = loadClasses(patcherJar, progress);
    if (classMap == null) {
      return false;
    }

    File patchFile = getPatchFile(patch, p, downloader, settings, progress);
    if (patchFile == null) {
      progress.logWarning("Patch failed to download.");
      return false;
    }

    final File tempDir = FileOpUtils.getNewTempDir("PatchInstaller", fop);
    if (tempDir == null) {
      progress.logWarning("Failed to create temporary directory");
      return false;
    }
    try {
      try {
        FileOpUtils.recursiveCopy(local.getLocation(), tempDir, fop);
      }
      catch (IOException e) {
        progress.logWarning("Failed to copy package to temporary location", e);
        return false;
      }
      fop.delete(new File(tempDir, LocalRepoLoader.PACKAGE_XML_FN));

      boolean result = runInstaller(progress, tempDir, patchFile, classMap.get(RUNNER_CLASS_NAME), classMap.get(UPDATER_UI_CLASS_NAME),
                                    classMap.get(REPO_UI_CLASS_NAME));
      if (!result) {
        return false;
      }
      try {
        InstallerUtil.writePackageXml(p, tempDir, manager, fop, progress);
      }
      catch (IOException e) {
        progress.logWarning("Failed to write new package.xml");
        return false;
      }

      progress.logInfo("Moving files into place...");
      try {
        FileOpUtils.safeRecursiveOverwrite(tempDir, local.getLocation(), fop, progress);
      }
      catch (IOException e) {
        progress.logWarning("Failed to move patched files into place", e);
        return false;
      }
    }
    finally {
      fop.deleteFileOrFolder(tempDir);
    }
    progress.logInfo("Done");
    progress.setFraction(1);
    progress.setIndeterminate(false);

    manager.markInvalid();
    return true;
  }

  /**
   * Run the installer by reflection.
   *
   * @see #install(RemotePackage, Downloader, SettingsController, ProgressIndicator, RepoManager, FileOp)
   */
  @SuppressWarnings("unchecked")
  @VisibleForTesting
  static boolean runInstaller(@NotNull ProgressIndicator progress,
                              @NotNull File localPackageLocation,
                              @NotNull File patchFile,
                              @NotNull Class runnerClass,
                              @NotNull Class uiBaseClass,
                              @NotNull Class uiClass) {
    Object ui;
    try {
      ui = uiClass.getConstructor(ProgressIndicator.class).newInstance(progress);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to create updater ui!", e);
      return false;
    }

    Method initLogger;
    try {
      initLogger = runnerClass.getMethod("initLogger");
      initLogger.invoke(null);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to initialize logger!", e);
      return false;
    }

    final Method doInstall;
    try {
      doInstall = runnerClass.getMethod("doInstall", String.class, uiBaseClass, String.class);
    }
    catch (Throwable e) {
      progress.logWarning("Failed to find main method in runner!", e);
      return false;
    }

    try {
      progress.logInfo("Running patch installer...");
      if (!(Boolean)doInstall.invoke(null, patchFile.getPath(), ui, localPackageLocation.getPath())) {
        progress.logWarning("Failed to apply patch");
        return false;
      }
      progress.logInfo("Patch applied.");
    }
    catch (Throwable e) {
      progress.logWarning("Failed to run patcher", e);
      return false;
    }

    return true;
  }

  /**
   * Resolves and downloads the patch for the given {@link Archive.PatchType}.
   *
   * @return {@code null} if unsuccessful.
   */
  @Nullable
  @VisibleForTesting
  static File getPatchFile(@NotNull Archive.PatchType patch,
                           @NotNull RemotePackage p,
                           @NotNull Downloader downloader,
                           @Nullable SettingsController settings,
                           @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveUrl(patch.getUrl(), p, progress);
    if (url == null) {
      progress.logWarning("Failed to resolve URL: " + patch.getUrl());
      return null;
    }
    final File patchFile;
    try {
      patchFile = downloader.downloadFully(url, settings, progress);
    }
    catch (IOException e) {
      progress.logWarning("Error during downloading", e);
      return null;
    }
    return patchFile;
  }

  /**
   * Loads the patcher classes needed to apply the patches for the given {@link RemotePackage}.
   *
   * @return Map of class names to {@link Class} objects for the classes need to run the patcher.
   */
  @Nullable
  private Map<String, Class<?>> loadClasses(@NotNull File patcherJar,
                                            @NotNull ProgressIndicator progress) {
    Map<String, Class<?>> result = ourPatcherCache.get(patcherJar);
    if (result != null) {
      return result;
    }

    ClassLoader loader = getClassLoader(progress, patcherJar);
    if (loader == null) {
      return null;
    }

    result = loadClasses(progress, loader);
    if (result == null) {
      return null;
    }
    ourPatcherCache.put(patcherJar, result);
    return result;
  }

  /**
   * Loads the required classes (or returns cached versions if available).
   *
   * @return A map from classname to class instance, or null if the required classes were not found.
   */
  @Nullable
  private static Map<String, Class<?>> loadClasses(@NotNull ProgressIndicator progress, @NotNull ClassLoader loader) {
    Map<String, Class<?>> result;
    result = Maps.newHashMap();
    try {
      result.put(RUNNER_CLASS_NAME, Class.forName(RUNNER_CLASS_NAME, true, loader));
      result.put(UPDATER_UI_CLASS_NAME, Class.forName(UPDATER_UI_CLASS_NAME, true, loader));
      result.put(REPO_UI_CLASS_NAME, Class.forName(REPO_UI_CLASS_NAME, true, loader));
    }
    catch (Throwable e) {
      progress.logWarning("Failed to find installer classes", e);
      return null;
    }
    return result;
  }

  /**
   * Gets a class loader for the given jar.
   */
  @Nullable
  private ClassLoader getClassLoader(@NotNull ProgressIndicator progress, @NotNull File patcherJar) {
    ClassLoader loader;
    try {
      loader = UrlClassLoader.build().urls(patcherJar.toURI().toURL()).parent(getClass().getClassLoader()).get();
    }
    catch (MalformedURLException e) {
      // Shouldn't happen
      progress.logError("Failed to create URL from file: " + patcherJar, e);
      return null;
    }
    return loader;
  }

  /**
   * Gets the patcher jar required by the given {@link RemotePackage}.
   *
   * @return The location of the patcher.jar, or null if it was not found.
   */
  @Nullable
  @VisibleForTesting
  static File getPatcherFile(@NotNull RemotePackage p,
                             @NotNull Map<String, ? extends LocalPackage> localPackages,
                             @NotNull ProgressIndicator progress, @NotNull FileOp fop) {
    File patcherJar = null;
    for (Dependency d : p.getAllDependencies()) {
      if (d.getPath().startsWith(PATCHER_PATH_PREFIX)) {
        LocalPackage patcher = localPackages.get(d.getPath());
        if (patcher != null) {
          patcherJar = new File(patcher.getLocation(), PATCHER_JAR_FN);
          break;
        }
      }
    }
    if (patcherJar == null || !fop.isFile(patcherJar)) {
      progress.logWarning("Failed to find patcher!");
      return null;
    }
    return patcherJar;
  }
}
