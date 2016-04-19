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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.installer.AbstractInstallerFactory;
import com.android.repository.impl.installer.AbstractPackageOperation;
import com.android.repository.impl.manager.LocalRepoLoaderImpl;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Map;

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
   * Directory under the sdk root where we keep pending patches that may require restart.
   */
  private static final String PATCHES_DIR_NAME = ".patches";

  /**
   * Filename of the patch jar itself.
   */
  private static final String PATCH_JAR_FN = "patch.jar";

  /**
   * Prefix of dirs containing individual patches.
   */
  private static final String PATCH_DIR_PREFIX = PatchInstallerFactory.class.getSimpleName();

  /**
   * The actual patch file itself, inside the patch jar.
   */
  private static final String PATCH_ZIP_FN = "patch-file.zip";

  /**
   * Temporary path for the package.xml during install, so the patcher doesn't complain about it being there. It will be moved back into
   * place if there's a problem.
   */
  private static final String OLD_PACKAGE_XML_FN = "package.xml.old";

  /**
   * Cache of patcher classes. Key is jar file, subkey is class name.
   */
  private static Map<File, Map<String, Class<?>>> ourPatcherCache = new WeakHashMap<>();

  /**
   * Find any pending patches under the given sdk root that require studio to be restarted, and if there are any, restart and install them.
   * If they have already been installed (as indicated by the patch itself being missing, and the revision mentioned in source.properties
   * matching that in the pending XML, do the install complete actions (write package.xml and fire listeners) and clean up.
   */
  public static void restartAndInstallIfNecessary(File androidSdkPath) {
    File patchesDir = new File(androidSdkPath, PATCHES_DIR_NAME);
    AndroidSdkHandler handler = AndroidSdkHandler.getInstance(androidSdkPath);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    FileOp fop = FileOpUtils.create();
    if (patchesDir.exists()) {
      for (File patchDir : patchesDir.listFiles((File file) -> file.isDirectory() && file.getName().startsWith(PATCH_DIR_PREFIX))) {
        processPatch(androidSdkPath, handler, progress, fop, patchDir);
      }
    }
  }

  /**
   * Either restart and install the given patch or delete it (if it's already installed).
   */
  private static void processPatch(File androidSdkPath,
                                   AndroidSdkHandler handler,
                                   StudioLoggerProgressIndicator progress,
                                   FileOp fop,
                                   File patchDir) {
    RemotePackage pendingPackage = null;
    File installDir = null;
    try {
      RepoManager mgr = handler.getSdkManager(progress);
      pendingPackage = InstallerUtil.readPendingPackageXml(patchDir, mgr, fop, progress);
      if (pendingPackage != null) {
        File patch = new File(patchDir, PATCH_JAR_FN);
        installDir = pendingPackage.getInstallDir(mgr, progress);
        File existingPackageXml = new File(installDir, LocalRepoLoaderImpl.PACKAGE_XML_FN);
        File oldPackageXml = new File(patchDir, OLD_PACKAGE_XML_FN);
        if (patch.exists() && existingPackageXml.renameTo(oldPackageXml)) {
          // This will exit the app.
          Main.installPatch("sdk", PATCH_JAR_FN, FileUtil.getTempDirectory(), patch, installDir.getAbsolutePath());
        }
        else {
          // The patch is already installed, or was cancelled.

          String relativePath = FileOpUtils.makeRelative(androidSdkPath, installDir, fop);
          // Use the old mechanism to get the version, since it's actually part of the package itself. Thus we can tell if the patch
          // has already been applied.
          Revision rev = AndroidCommonUtils.parsePackageRevision(androidSdkPath.getPath(), relativePath);
          if (rev != null && rev.equals(pendingPackage.getVersion())) {
            InstallerFactory dummyFactory = new DummyInstallerFactory(pendingPackage);
            dummyFactory.setListenerFactory(new StudioSdkInstallListenerFactory(handler));
            Installer installer = dummyFactory.createInstaller(pendingPackage, mgr, fop);
            installer.completeInstall(progress);
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
      fop.deleteFileOrFolder(patchDir);
    }
    catch (Exception e) {
      progress.logWarning("Problem during patch cleanup", e);
    }
  }

  @NotNull
  @Override
  protected Installer doCreateInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr, @NonNull FileOp fop) {
    return new PatchInstaller(p, mgr, fop);
  }

  @NotNull
  @Override
  protected Uninstaller doCreateUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr, @NonNull FileOp fop) {
    return new PatchUninstaller(p, mgr, fop);
  }

  /**
   * Installer for binary diff packages, as built by {@code com.intellij.updater.Runner}.
   */
  public static class PatchInstaller extends AbstractPackageOperation.AbstractInstaller {

    public PatchInstaller(@NotNull RemotePackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
      super(p, mgr, fop);
    }

    @Override
    protected boolean doPrepareInstall(@NotNull File tempDir,
                                       @NotNull Downloader downloader,
                                       @NotNull ProgressIndicator progress) {
      Map<String, ? extends LocalPackage> localPackages = getRepoManager().getPackages().getLocalPackages();
      LocalPackage local = localPackages.get(getPackage().getPath());
      assert local != null;
      RemotePackage p = getPackage();
      Archive archive = p.getArchive();
      assert archive != null;
      Archive.PatchType patch = archive.getPatch(local.getVersion());
      assert patch != null;

      File patchFile = getPatchFile(patch, p, tempDir, downloader, progress);
      if (patchFile == null) {
        progress.logWarning("Patch failed to download.");
        return false;
      }
      return true;
    }

    @Override
    protected boolean doCompleteInstall(@Nullable File installTempPath,
                                        @NotNull File destination,
                                        @NotNull ProgressIndicator progress) {
      if (installTempPath == null) {
        return false;
      }
      Map<String, ? extends LocalPackage> localPackages = getRepoManager().getPackages().getLocalPackages();
      File patcherJar = getPatcherFile(localPackages, progress);
      if (patcherJar == null) {
        progress.logWarning("Couldn't find patcher jar!");
        return false;
      }

      Map<String, Class<?>> classMap = loadClasses(patcherJar, progress);
      if (classMap == null) {
        return false;
      }

      // The patcher won't expect this to be in the target directory, so delete it beforehand.
      mFop.deleteFileOrFolder(new File(destination, InstallerUtil.INSTALLER_DIR_FN));

      // Move the package.xml away, since the installer won't expect that either. But we want to be able to move it back if need be.
      File existingPackageXml = new File(destination, LocalRepoLoaderImpl.PACKAGE_XML_FN);
      File tempPackageXml = new File(installTempPath, LocalRepoLoaderImpl.PACKAGE_XML_FN);
      mFop.renameTo(existingPackageXml, tempPackageXml);

      boolean result;
      File patchFile = new File(installTempPath, "patch.jar");
      try {
        result = runPatcher(progress, destination, patchFile, classMap.get(RUNNER_CLASS_NAME),
                            classMap.get(UPDATER_UI_CLASS_NAME), classMap.get(REPO_UI_CLASS_NAME));
      }
      catch (RestartRequiredException e) {
        askAboutRestart(patchFile, patcherJar, progress);
        result = false;
      }
      if (!result) {
        // We cancelled or selected restart later, or there was some problem. Move package.xml back into place.
        mFop.renameTo(tempPackageXml, existingPackageXml);
        return false;
      }
      progress.logInfo("Done");

      return true;
    }

    /**
     * If a patch fails to install because Studio is locking some of the files, we have to restart studio. Ask if the user wants
     * to, and then move things into place so they can be picked up on restart.
     */
    private void askAboutRestart(@NonNull final File patchFile,
                                 @NonNull File patcherFile,
                                 @NonNull final ProgressIndicator progress) {
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
        int result = Messages.showDialog(
          (Project)null,
          String.format(
            "%1$s is currently in use by %2$s and cannot be updated. Please restart to complete installation.",
            getPackage().getDisplayName(),
            names.getFullProductName()),
          "Restart Required", options, options.length - 1, AllIcons.General.QuestionDialog);
        if (result == 0) {
          progress.logInfo("Cancelled");
        }
        else {
          if (setupPatchDir(patchFile, patcherFile, progress)) {
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
    private boolean setupPatchDir(@NonNull File patchFile, @NonNull File patcherFile, @NonNull ProgressIndicator progress) {
      File patchesDir = new File(getRepoManager().getLocalPath(), PATCHES_DIR_NAME);
      File patchDir;
      for (int i = 1; ; i++) {
        patchDir = new File(patchesDir, PATCH_DIR_PREFIX + i);
        if (!mFop.exists(patchDir)) {
          mFop.mkdirs(patchDir);
          break;
        }
      }
      try {
        File completePatch = new File(patchDir, PATCH_JAR_FN);
        mFop.copyFile(patcherFile, completePatch);
        FileSystem completeFs = FileSystems.newFileSystem(URI.create("jar:" + completePatch.toURI().toString()), Maps.newHashMap());
        FileSystem patchFs = FileSystems.newFileSystem(URI.create("jar:" + patchFile.toURI().toString()), Maps.newHashMap());
        Files.copy(patchFs.getPath(PATCH_ZIP_FN), completeFs.getPath(PATCH_ZIP_FN));
        completeFs.close();
        patchFs.close();
        InstallerUtil.writePendingPackageXml(getPackage(), patchDir, getRepoManager(), mFop, progress);
      }
      catch (IOException e) {
        progress.logWarning("Error while setting up patch.", e);
        return false;
      }
      return true;
    }

    /**
     * Gets the patcher jar required by our package.
     *
     * @return The location of the patcher.jar, or null if it was not found.
     */
    @Nullable
    @VisibleForTesting
    File getPatcherFile(@NotNull Map<String, ? extends LocalPackage> localPackages,
                        @NotNull ProgressIndicator progress) {
      File patcherJar = null;
      for (Dependency d : getPackage().getAllDependencies()) {
        if (d.getPath().startsWith(PATCHER_PATH_PREFIX)) {
          LocalPackage patcher = localPackages.get(d.getPath());
          if (patcher != null) {
            patcherJar = new File(patcher.getLocation(), PATCHER_JAR_FN);
            break;
          }
        }
      }
      if (patcherJar == null || !mFop.isFile(patcherJar)) {
        progress.logWarning("Failed to find patcher!");
        return null;
      }
      return patcherJar;
    }
  }

  /**
   * TODO: right now this just deletes the package, but it should construct a diff package on the fly that removes all the files in the
   * component and then runs it (to deal with e.g. in-use files).
   */
  public static class PatchUninstaller extends AbstractPackageOperation.AbstractUninstaller {
    public PatchUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
      super(p, mgr, fop);
    }

    @Override
    protected boolean doUninstall(@NonNull ProgressIndicator progress) {
      // TODO: use patch mechanism
      try {
        mFop.deleteFileOrFolder(getPackage().getLocation());
      }
      catch (Exception e) {
        return false;
      }
      return true;
    }
  }

  /**
   * Run the patcher by reflection.
   */
  @SuppressWarnings("unchecked")
  @VisibleForTesting
  static boolean runPatcher(@NotNull ProgressIndicator progress,
                            @NotNull File localPackageLocation,
                            @NotNull File patchFile,
                            @NotNull Class runnerClass,
                            @NotNull Class uiBaseClass,
                            @NotNull Class uiClass) throws RestartRequiredException {
    Object ui;
    try {
      ui = uiClass.getConstructor(Component.class, ProgressIndicator.class).newInstance(null, progress);
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
    catch (InvocationTargetException e) {
      if (e.getTargetException() instanceof RestartRequiredException) {
        throw (RestartRequiredException)e.getTargetException();
      }
      progress.logWarning("Failed to run patcher", e);
      return false;
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
                           @NotNull File tempDir,
                           @NotNull Downloader downloader,
                           @NotNull ProgressIndicator progress) {
    URL url = InstallerUtil.resolveUrl(patch.getUrl(), p, progress);
    if (url == null) {
      progress.logWarning("Failed to resolve URL: " + patch.getUrl());
      return null;
    }
    try {
      File patchFile = new File(tempDir, "patch.jar");
      downloader.downloadFully(url, patchFile, progress);
      return patchFile;
    }
    catch (IOException e) {
      progress.logWarning("Error during downloading", e);
      return null;
    }
  }

  /**
   * Loads the patcher classes needed to apply the patches for the given {@link RemotePackage}.
   *
   * @return Map of class names to {@link Class} objects for the classes need to run the patcher.
   */
  @Nullable
  private static Map<String, Class<?>> loadClasses(@NotNull File patcherJar,
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
  private static ClassLoader getClassLoader(@NotNull ProgressIndicator progress, @NotNull File patcherJar) {
    ClassLoader loader;
    try {
      loader = UrlClassLoader.build().urls(patcherJar.toURI().toURL()).parent(PatchInstaller.class.getClassLoader()).get();
    }
    catch (MalformedURLException e) {
      // Shouldn't happen
      progress.logError("Failed to create URL from file: " + patcherJar, e);
      return null;
    }
    return loader;
  }

  public static class RestartRequiredException extends RuntimeException {}

  private static class DummyInstallerFactory extends AbstractInstallerFactory {
    private final RemotePackage myPendingPackage;

    public DummyInstallerFactory(RemotePackage pendingPackage) {
      myPendingPackage = pendingPackage;
    }

    @NonNull
    @Override
    protected Installer doCreateInstaller(@NonNull RemotePackage p, @NonNull RepoManager mgr, @NonNull FileOp fop) {
      return new AbstractPackageOperation.AbstractInstaller(myPendingPackage, mgr, fop) {
        @Override
        protected boolean doCompleteInstall(@com.android.annotations.Nullable File installTemp,
                                            @NonNull File dest,
                                            @NonNull ProgressIndicator progress) {
          return true;
        }

        @Override
        protected boolean doPrepareInstall(@NonNull File installTempPath,
                                           @NonNull Downloader downloader,
                                           @NonNull ProgressIndicator progress) {
          return false;
        }
      };
    }

    @NonNull
    @Override
    protected Uninstaller doCreateUninstaller(@NonNull LocalPackage p, @NonNull RepoManager mgr, @NonNull FileOp fop) {
      throw new UnsupportedOperationException();
    }
  }
}
