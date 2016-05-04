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
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.HashMap;
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
  private static final String REPO_UI_CLASS_NAME = "com.android.tools.idea.sdk.updater.RepoUpdaterUI";

  /**
   * Class that can generate patches that this patcher can read.
   */
  private static final String PATCH_GENERATOR_CLASS_NAME = "com.android.tools.idea.sdk.updater.PatchGenerator";

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
  public static void restartAndInstallIfNecessary(@NotNull File androidSdkPath) {
    File patchesDir = new File(androidSdkPath, PATCHES_DIR_NAME);
    AndroidSdkHandler handler = AndroidSdkHandler.getInstance(androidSdkPath);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    FileOp fop = FileOpUtils.create();
    if (patchesDir.exists()) {
      for (File patchDir : patchesDir.listFiles((File file) -> file.isDirectory() && file.getName().startsWith(PATCH_DIR_PREFIX))) {
        processPatch(androidSdkPath, handler, patchDir, fop, progress);
      }
    }
  }

  /**
   * Either restart and install the given patch or delete it (if it's already installed).
   */
  private static void processPatch(@NotNull File androidSdkPath,
                                   @NotNull AndroidSdkHandler handler,
                                   @NotNull File patchDir,
                                   @NotNull FileOp fop,
                                   @NotNull ProgressIndicator progress) {
    RemotePackage pendingPackage = null;
    File installDir = null;
    try {
      RepoManager mgr = handler.getSdkManager(progress);
      pendingPackage = (RemotePackage)InstallerUtil.readPendingPackageXml(patchDir, mgr, fop, progress);
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
            // We need to make sure the listeners are fired, so create an installer that does nothing and invoke it.
            InstallerFactory dummyFactory = new DummyInstallerFactory(pendingPackage);
            dummyFactory.setListenerFactory(new StudioSdkInstallListenerFactory(handler));
            Installer installer = dummyFactory.createInstaller(pendingPackage, mgr, new StudioDownloader(), fop);
            installer.complete(progress);
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
  protected Installer doCreateInstaller(@NotNull RemotePackage p,
                                        @NotNull RepoManager mgr,
                                        @NotNull Downloader downloader,
                                        @NotNull FileOp fop) {
    LocalPackage local = mgr.getPackages().getLocalPackages().get(p.getPath());
    Archive archive = p.getArchive();
    assert archive != null;
    if (local != null && archive.getPatch(local.getVersion()) != null) {
      new PatchInstaller(local, p, mgr, downloader, fop);
    }
    return new FullInstaller(local, p, mgr, downloader, fop);
  }

  @NotNull
  @Override
  protected Uninstaller doCreateUninstaller(@NotNull LocalPackage p, @NotNull RepoManager mgr, @NotNull FileOp fop) {
    return new PatchUninstaller(p, mgr, fop);
  }

  public static boolean canHandlePackage(@NotNull RepoPackage p, @NotNull AndroidSdkHandler handler) {
    if (p instanceof LocalPackage) {
      // Once uninstaller is implemented this needs to check for an installed patcher
      return true;
    }
    ProgressIndicator progress = new StudioLoggerProgressIndicator(PatchInstallerFactory.class);
    return getPatcherFile((RemotePackage)p, handler.getSdkManager(progress), handler.getFileOp(), progress) != null;
  }

  /**
   * Gets the patcher jar required by our package.
   *
   * @return The location of the patcher.jar, or null if it was not found.
   */
  @VisibleForTesting
  @Nullable
  static File getPatcherFile(@NotNull RemotePackage remote, @NotNull RepoManager mgr, @NotNull FileOp fop,
                             @NotNull ProgressIndicator progress) {
    File patcherJar = null;

    for (Dependency d : remote.getAllDependencies()) {
      if (d.getPath().startsWith(PATCHER_PATH_PREFIX)) {
        LocalPackage patcher = mgr.getPackages().getLocalPackages().get(d.getPath());
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
  static File downloadPatchFile(@NotNull Archive.PatchType patch,
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
      downloader.downloadFully(url, patchFile, patch.getChecksum(), progress);
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
    result = new HashMap<>();
    try {
      result.put(RUNNER_CLASS_NAME, Class.forName(RUNNER_CLASS_NAME, true, loader));
      result.put(UPDATER_UI_CLASS_NAME, Class.forName(UPDATER_UI_CLASS_NAME, true, loader));
      result.put(REPO_UI_CLASS_NAME, Class.forName(REPO_UI_CLASS_NAME, true, loader));
      result.put(PATCH_GENERATOR_CLASS_NAME, Class.forName(PATCH_GENERATOR_CLASS_NAME, true, loader));
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

  abstract static class PatchInstallerBase extends AbstractPackageOperation.AbstractInstaller {

    protected final LocalPackage myExisting;

    public PatchInstallerBase(@Nullable LocalPackage existing,
                              @NotNull RemotePackage p,
                              @NotNull RepoManager mgr,
                              @NotNull Downloader downloader,
                              @NotNull FileOp fop) {
      super(p, mgr, downloader, fop);
      myExisting = existing;
    }

    @Override
    protected boolean doComplete(@Nullable File installTempPath,
                                 @NotNull File destination,
                                 @NotNull ProgressIndicator progress) {
      if (installTempPath == null) {
        return false;
      }
      File patcherJar = getPatcherFile(getPackage(), getRepoManager(), mFop, progress);
      if (patcherJar == null) {
        progress.logWarning("Couldn't find patcher JAR!");
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
      File patchFile = new File(installTempPath, PATCH_JAR_FN);
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
     * If a patch fails to install because Studio is locking some of the files, we have to restart Studio. Ask if the user wants
     * to, and then move things into place so they can be picked up on restart.
     */
    private void askAboutRestart(@NotNull final File patchFile,
                                 @NotNull File patcherFile,
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
    private boolean setupPatchDir(@NotNull File patchFile, @NotNull File patcherFile, @NotNull ProgressIndicator progress) {
      File patchesDir = new File(getRepoManager().getLocalPath(), PATCHES_DIR_NAME);
      File patchDir;
      for (int i = 1; ; i++) {
        patchDir = new File(patchesDir, PATCH_DIR_PREFIX + i);
        if (!mFop.exists(patchDir)) {
          mFop.mkdirs(patchDir);
          break;
        }
      }
      File completePatch = new File(patchDir, PATCH_JAR_FN);
      try (FileSystem completeFs = FileSystems.newFileSystem(URI.create("jar:" + completePatch.toURI()), new HashMap<>());
           FileSystem patchFs = FileSystems.newFileSystem(URI.create("jar:" + patchFile.toURI().toString()), new HashMap<>())) {
        mFop.copyFile(patcherFile, completePatch);
        Files.copy(patchFs.getPath(PATCH_ZIP_FN), completeFs.getPath(PATCH_ZIP_FN));
        InstallerUtil.writePendingPackageXml(getPackage(), patchDir, getRepoManager(), mFop, progress);
      }
      catch (IOException e) {
        progress.logWarning("Error while setting up patch.", e);
        return false;
      }
      return true;
    }
  }

  public static class FullInstaller extends PatchInstallerBase {

    private static final String UNZIP_DIR_FN = "unzip";

    protected FullInstaller(@Nullable LocalPackage existing,
                            @NotNull RemotePackage p,
                            @NotNull RepoManager mgr,
                            @NotNull Downloader downloader,
                            @NotNull FileOp fop) {
      super(existing, p, mgr, downloader, fop);
    }

    @Override
    protected boolean doPrepare(@NotNull File installTempPath, @NotNull ProgressIndicator progress) {
      if (!downloadAndUnzip(installTempPath, progress)) {
        return false;
      }
      File pkgRoot = new File(installTempPath, UNZIP_DIR_FN);
      File[] children = mFop.listFiles(pkgRoot);
      if (children.length == 1) {
        // This is the expected case
        pkgRoot = children[0];
      }

      File patcherJar = getPatcherFile(getPackage(), getRepoManager(), mFop, progress);
      if (patcherJar == null) {
        progress.logWarning("Couldn't find patcher JAR!");
        return false;
      }

      Map<String, Class<?>> classMap = loadClasses(patcherJar, progress);
      if (classMap == null) {
        return false;
      }

      File existingRoot = myExisting == null ? null : myExisting.getLocation();
      String existingDescription = myExisting == null ? "None" : myExisting.getDisplayName() + " Revision " + myExisting.getVersion();
      String description = "Version " + getPackage().getVersion();
      Class<?> patchGeneratorClass = classMap.get(PATCH_GENERATOR_CLASS_NAME);
      try {
        Method generateMethod = patchGeneratorClass.getMethod("generateFullPackage", File.class, File.class, File.class, String.class,
                                                              String.class, ProgressIndicator.class);
        return (Boolean)generateMethod.invoke(null, pkgRoot, existingRoot,
                                              new File(installTempPath, PATCH_JAR_FN), existingDescription, description, progress);
      }
      catch (NoSuchMethodException e) {
        progress.logWarning("Patcher doesn't support full package generation!");
        return false;
      }
      catch (InvocationTargetException | IllegalAccessException e) {
        progress.logWarning("Patch generation failed!");
        return false;
      }
    }

    private boolean downloadAndUnzip(@NotNull File installTempPath, @NotNull ProgressIndicator progress) {
      URL url = InstallerUtil.resolveCompleteArchiveUrl(getPackage(), progress);
      if (url == null) {
        progress.logWarning("No compatible archive found!");
        return false;
      }
      Archive archive = getPackage().getArchive();
      assert archive != null;
      try {
        File downloadLocation = new File(installTempPath, url.getFile());
        String checksum = archive.getComplete().getChecksum();
        getDownloader().downloadFully(url, downloadLocation, checksum, progress);
        if (progress.isCanceled()) {
          return false;
        }
        if (!mFop.exists(downloadLocation)) {
          progress.logWarning("Failed to download package!");
          return false;
        }
        File unzip = new File(installTempPath, UNZIP_DIR_FN);
        mFop.mkdirs(unzip);
        InstallerUtil.unzip(downloadLocation, unzip, mFop,
                            archive.getComplete().getSize(), progress);
        if (progress.isCanceled()) {
          return false;
        }
        mFop.delete(downloadLocation);

        return true;
      } catch (IOException e) {
        StringBuilder message =
          new StringBuilder("An error occurred while preparing SDK package ")
            .append(getPackage().getDisplayName());
        String exceptionMessage = e.getMessage();
        if (exceptionMessage != null && !exceptionMessage.isEmpty()) {
          message.append(": ")
            .append(exceptionMessage);
        }
        else {
          message.append(".");
        }
        progress.logWarning(message.toString(), e);
      }
      return false;
    }
  }

  /**
   * Installer for binary diff packages, as built by {@code com.intellij.updater.Runner}.
   */
  public static class PatchInstaller extends PatchInstallerBase {

    public PatchInstaller(@Nullable LocalPackage existing,
                          @NotNull RemotePackage p,
                          @NotNull RepoManager mgr,
                          @NotNull Downloader downloader,
                          @NotNull FileOp fop) {
      super(existing, p, mgr, downloader, fop);
    }

    @Override
    protected boolean doPrepare(@NotNull File tempDir,
                                @NotNull ProgressIndicator progress) {
      LocalPackage local = getRepoManager().getPackages().getLocalPackages().get(getPackage().getPath());
      Archive archive = getPackage().getArchive();
      assert archive != null;

      RemotePackage p = getPackage();
      Archive.PatchType patch = archive.getPatch(local.getVersion());
      assert patch != null;

      File patchFile = downloadPatchFile(patch, p, tempDir, getDownloader(), progress);
      if (patchFile == null) {
        progress.logWarning("Patch failed to download.");
        return false;
      }
      return true;
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
    protected boolean doPrepare(@NotNull ProgressIndicator progress) {
      return true;
    }

    @Override
    protected boolean doComplete(@NotNull ProgressIndicator progress) {
      // TODO: use patch mechanism
      try {
        mFop.deleteFileOrFolder(getPackage().getLocation());
        return true;
      }
      catch (Exception e) {
        return false;
      }
    }
  }

  public static class RestartRequiredException extends RuntimeException {}

  private static class DummyInstallerFactory extends AbstractInstallerFactory {
    private final RemotePackage myPendingPackage;

    public DummyInstallerFactory(RemotePackage pendingPackage) {
      myPendingPackage = pendingPackage;
    }

    @NotNull
    @Override
    protected Installer doCreateInstaller(@NotNull RemotePackage p,
                                          @NotNull RepoManager mgr,
                                          @NotNull Downloader downloader,
                                          @NotNull FileOp fop) {
      return new AbstractPackageOperation.AbstractInstaller(myPendingPackage, mgr, downloader, fop) {
        @Override
        protected boolean doComplete(@Nullable File installTemp,
                                     @NotNull File dest,
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
      throw new UnsupportedOperationException();
    }
  }
}
