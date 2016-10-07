/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.android.tools.idea.sdk.IdeSdks.getAndroidSdkPath;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.google.common.io.Closeables.close;
import static com.google.common.io.Files.toByteArray;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * The Project data that needs to be persisted to check whether it is possible to reload the Project without the need of calling Gradle.
 */
public class GradleProjectSyncData implements Serializable {
  @NotNull @NonNls private static final String STATE_FILE_NAME = "gradle_project_sync_data.bin";
  private static final boolean ENABLED = !Boolean.getBoolean("studio.disable.synccache");

  private static final Logger LOG = Logger.getInstance(GradleProjectSyncData.class);

  /**
   * A set of files and their MD5 that the persisted external project data depends on.
   */
  private Map<String, byte[]> myFileChecksums = Maps.newHashMap();

  /**
   * The model version
   */
  @SuppressWarnings("FieldCanBeLocal")
  private String myGradlePluginVersion = GRADLE_PLUGIN_RECOMMENDED_VERSION;

  /**
   * The last time a sync was done.
   */
  private long myLastGradleSyncTimestamp = -1L;

  private transient File myRootDirPath;

  private GradleProjectSyncData() {
  }

  /**
   * Creates an instance by loading the persisted data from the disk for the given project.
   *
   * @param project the project for which to load the data.
   * @return the loaded instance or {@code null} when the data ia not available or no longer valid.
   */
  @Nullable
  public static GradleProjectSyncData getInstance(@NotNull final Project project) {
    if (!ENABLED || needsAndroidSdkSync(project)) {
      return null;
    }
    try {
      return doLoadFromDisk(project);
    }
    catch (IOException e) {
      LOG.info(String.format("Error accessing state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    catch (ClassNotFoundException e) {
      LOG.info(String.format("Cannot recover state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    return null;
  }

  private static boolean needsAndroidSdkSync(@NotNull final Project project) {
    if (isAndroidStudio()) {
      final File ideSdkPath = getAndroidSdkPath();
      if (ideSdkPath != null) {
        try {
          LocalProperties localProperties = new LocalProperties(project);
          File projectSdkPath = localProperties.getAndroidSdkPath();
          return projectSdkPath == null || !filesEqual(ideSdkPath, projectSdkPath);
        }
        catch (IOException ignored) {
        }
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static GradleProjectSyncData doLoadFromDisk(@NotNull Project project) throws IOException, ClassNotFoundException {
    FileInputStream fin = null;
    try {
      File rootDirPath = getBaseDirPath(project);
      File dataFile = getProjectStateFile(project);
      if (!dataFile.exists()) {
        return null;
      }
      fin = new FileInputStream(dataFile);
      ObjectInputStream ois = new ObjectInputStream(fin);
      try {
        GradleProjectSyncData data = (GradleProjectSyncData)ois.readObject();
        data.myRootDirPath = rootDirPath;
        return data;
      }
      finally {
        close(ois, false);
      }
    }
    finally {
      close(fin, false);
    }
  }

  /**
   * Persists the gradle sync data of this project to disk.
   *
   * @param project the project to get the data from.
   */
  public static void save(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    boolean cacheSaved = false;
    try {
      GradleProjectSyncData data = createFrom(project);
      if (data != null) {
        File file = getProjectStateFile(project);
        ensureExists(file.getParentFile());
        data.saveTo(file);
        cacheSaved = true;
      }
    }
    catch (IOException e) {
      LOG.info(String.format("Error while saving persistent state from project '%1$s'", project.getName()), e);
    }
    if (!cacheSaved) {
      LOG.info("Failed to generate new cache. Deleting the old one.");
      removeFrom(project);
    }
  }

  @Nullable
  @VisibleForTesting
  static GradleProjectSyncData createFrom(@NotNull Project project) throws IOException {
    GradleProjectSyncData data = new GradleProjectSyncData();
    File rootDirPath = getBaseDirPath(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        GradleModel gradleModel = gradleFacet.getGradleModel();
        if (gradleModel != null) {
          data.addFileChecksum(rootDirPath, gradleModel.getBuildFile());
        }
        else {
          LOG.warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
      }

      if (isGradleProjectModule(module)) {
        data.addFileChecksum(rootDirPath, getGradleBuildFile(module));
        data.addFileChecksum(rootDirPath, getGradleSettingsFile(rootDirPath));
        data.addFileChecksum(rootDirPath, new File(rootDirPath, FN_GRADLE_PROPERTIES));
        data.addFileChecksum(rootDirPath, new File(rootDirPath, FN_LOCAL_PROPERTIES));
        data.addFileChecksum(rootDirPath, getGradleUserSettingsFile());
      }

      NativeAndroidGradleModel nativeAndroidModel = NativeAndroidGradleModel.get(module);
      if (nativeAndroidModel != null) {
        for (File externalBuildFile : nativeAndroidModel.getNativeAndroidProject().getBuildFiles()) {
          data.addFileChecksum(rootDirPath, externalBuildFile);
        }
      }
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    data.myLastGradleSyncTimestamp = syncState.getLastGradleSyncTimestamp();
    return data;
  }

  @NotNull
  private static File getProjectStateFile(@NotNull Project project) throws IOException {
    return new File(PathManager.getSystemPath(), join("external_build_system", "Projects", project.getLocationHash(), STATE_FILE_NAME));
  }

  private void addFileChecksum(File rootDirPath, @Nullable VirtualFile vf) throws IOException {
    addFileChecksum(rootDirPath, vf != null ? virtualToIoFile(vf) : null);
  }

  private void addFileChecksum(File rootDirPath, @Nullable File file) throws IOException {
    if (file == null) {
      return;
    }
    String key;
    if (isAncestor(rootDirPath, file, true)) {
      key = getRelativePath(rootDirPath, file);
    }
    else {
      key = file.getAbsolutePath();
    }
    myFileChecksums.put(key, createChecksum(file));
  }

  @NotNull
  private static byte[] createChecksum(@NotNull File file) throws IOException {
    // For files tracked by the IDE we get the content from the virtual files, otherwise we revert to io.
    VirtualFile vf = findFileByIoFile(file, true);
    byte[] data = new byte[] {};
    if (vf != null) {
      vf.refresh(false, false);
      if (vf.exists()) {
        data = vf.contentsToByteArray();
      }
    } else if (file.exists()) {
      data = toByteArray(file);
    }
    return Hashing.md5().hashBytes(data).asBytes();
  }

  /**
   * Saves the data on the given project location.
   *
   * @param file the file where to save this data.
   */
  private void saveTo(File file) throws IOException {
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(file);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      try {
        oos.writeObject(this);
      }
      finally {
        close(oos, false);
      }
    }
    finally {
      close(fos, false);
    }
  }

  public static void removeFrom(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    try {
      File stateFile = getProjectStateFile(project);
      if (stateFile.isFile()) {
        delete(stateFile);
      }
    }
    catch (IOException e) {
      LOG.warn(String.format("Failed to remove state for project %1$s'", project.getName()));
    }
  }

  /**
   * Verifies that whether the persisted external project data can be used to create the project or not.
   * <p/>
   * This validates that all the files that the external project data depends on, still have the same content checksum and that the gradle
   * model version is still the same.
   *
   * @return whether the data is still valid.
   * @throws IOException if there is a problem accessing these files.
   */
  public boolean canUseCachedProjectData() {
    if (!myGradlePluginVersion.equals(GRADLE_PLUGIN_RECOMMENDED_VERSION)) {
      return false;
    }

    for (Map.Entry<String, byte[]> entry : myFileChecksums.entrySet()) {
      File file = new File(entry.getKey());
      if (!file.isAbsolute()) {
        file = new File(myRootDirPath, file.getPath());
      }
      try {
        if (!Arrays.equals(entry.getValue(), createChecksum(file))) {
          return false;
        }
      }
      catch (IOException e) {
        return false;
      }
    }
    return true;
  }

  public long getLastGradleSyncTimestamp() {
    return myLastGradleSyncTimestamp;
  }

  @VisibleForTesting
  Map<String, byte[]> getFileChecksums() {
    return myFileChecksums;
  }
}
