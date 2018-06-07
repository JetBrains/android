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
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.hash.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleProjects.isGradleProjectModule;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getCacheFolderRootPath;
import static com.google.common.io.Files.toByteArray;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtilRt.EMPTY_BYTE_ARRAY;

/**
 * The Project data that needs to be persisted to check whether it is possible to reload the Project without the need of calling Gradle.
 */
public class ProjectBuildFileChecksums implements Serializable {
  // Key: build file path (relative if inside project). Value: MD5 hash of file.
  private Map<String, byte[]> myFileChecksums = new HashMap<>();

  /**
   * The last time a sync was done.
   */
  private long myLastGradleSyncTimestamp = -1L;

  private transient File myRootFolderPath;

  public static class Loader {
    @Nullable
    public ProjectBuildFileChecksums loadFromDisk(@NotNull Project project) {
      return findFor(project);
    }
  }

  private ProjectBuildFileChecksums() {
  }

  /**
   * Creates an instance by loading the persisted data from the disk for the given project.
   *
   * @param project the project for which to load the data.
   * @return the loaded instance or {@code null} when the data ia not available or no longer valid.
   */
  @Nullable
  public static ProjectBuildFileChecksums findFor(@NotNull Project project) {
    if (needsAndroidSdkSync(project)) {
      return null;
    }
    try {
      return loadFromDisk(project);
    }
    catch (IOException e) {
      getLog().warn(String.format("Error accessing state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    catch (ClassNotFoundException e) {
      getLog().warn(String.format("Cannot recover state cache for project '%1$s', sync will be needed.", project.getName()));
    }
    return null;
  }

  private static boolean needsAndroidSdkSync(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      File ideSdkPath = IdeSdks.getInstance().getAndroidSdkPath();
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
  private static ProjectBuildFileChecksums loadFromDisk(@NotNull Project project) throws IOException, ClassNotFoundException {
    File rootFolderPath = getBaseDirPath(project);
    File dataFilePath = getProjectStateFile(project);
    if (!dataFilePath.exists()) {
      return null;
    }
    try (FileInputStream fis = new FileInputStream(dataFilePath)) {
      try (ObjectInputStream ois = new ObjectInputStream(fis)) {
        ProjectBuildFileChecksums data = (ProjectBuildFileChecksums)ois.readObject();
        data.myRootFolderPath = rootFolderPath;
        return data;
      }
    }
  }

  /**
   * Persists the gradle sync data of this project to disk.
   *
   * @param project the project to get the data from.
   */
  public static void saveToDisk(@NotNull Project project) {
    boolean cacheSaved = false;
    try {
      ProjectBuildFileChecksums buildFileChecksums = createFrom(project);
      if (buildFileChecksums != null) {
        File file = getProjectStateFile(project);
        ensureExists(file.getParentFile());
        buildFileChecksums.saveTo(file);
        cacheSaved = true;
      }
    }
    catch (Throwable e) {
      getLog().info(String.format("Error while saving persistent state from project '%1$s'", project.getName()), e);
    }
    if (!cacheSaved) {
      getLog().info("Failed to generate new cache. Deleting the old one.");
      removeFrom(project);
    }
  }

  @VisibleForTesting
  @Nullable
  static ProjectBuildFileChecksums createFrom(@NotNull Project project) throws IOException {
    ProjectBuildFileChecksums buildFileChecksums = new ProjectBuildFileChecksums();
    File rootFolderPath = getBaseDirPath(project);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
        if (gradleModel == null) {
          getLog().warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
        buildFileChecksums.addFileChecksum(rootFolderPath, gradleModel.getBuildFile());
      }

      if (isGradleProjectModule(module)) {
        buildFileChecksums.addFileChecksum(rootFolderPath, getGradleBuildFile(module));
        buildFileChecksums.addFileChecksum(rootFolderPath, getGradleSettingsFile(rootFolderPath));
        buildFileChecksums.addFileChecksum(rootFolderPath, new File(rootFolderPath, FN_GRADLE_PROPERTIES));
        buildFileChecksums.addFileChecksum(rootFolderPath, new File(rootFolderPath, FN_LOCAL_PROPERTIES));
        buildFileChecksums.addFileChecksum(rootFolderPath, getGradleUserSettingsFile());
      }

      NdkModuleModel ndkModel = NdkModuleModel.get(module);
      if (ndkModel != null) {
        for (File externalBuildFile : ndkModel.getAndroidProject().getBuildFiles()) {
          buildFileChecksums.addFileChecksum(rootFolderPath, externalBuildFile);
        }
      }
    }

    GradleSyncState syncState = GradleSyncState.getInstance(project);
    buildFileChecksums.myLastGradleSyncTimestamp = syncState.getSummary().getSyncTimestamp();
    return buildFileChecksums;
  }

  @NotNull
  private static File getProjectStateFile(@NotNull Project project) throws IOException {
    return new File(getCacheFolderRootPath(project), "build_file_checksums.ser");
  }

  private void addFileChecksum(@NotNull File rootFolderPath, @Nullable VirtualFile file) throws IOException {
    if (file == null) {
      return;
    }
    addFileChecksum(rootFolderPath, virtualToIoFile(file));
  }

  private void addFileChecksum(@NotNull File rootFolderPath, @Nullable File file) throws IOException {
    if (file == null) {
      return;
    }
    String key;
    if (isAncestor(rootFolderPath, file, true)) {
      key = getRelativePath(rootFolderPath, file);
    }
    else {
      key = file.getAbsolutePath();
    }
    myFileChecksums.put(key, createChecksum(file));
  }

  /**
   * Saves the data on the given project location.
   *
   * @param file the file where to save this data.
   */
  private void saveTo(@NotNull File file) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
        oos.writeObject(this);
      }
    }
  }

  public static void removeFrom(@NotNull Project project) {
    try {
      File stateFile = getProjectStateFile(project);
      if (stateFile.isFile()) {
        delete(stateFile);
      }
    }
    catch (IOException e) {
      getLog().warn(String.format("Failed to remove state for project '%1$s'", project.getName()));
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ProjectBuildFileChecksums.class);
  }

  /**
   * Verifies that whether the persisted external project data can be used to create the project or not.
   * <p/>
   * This validates that all the files that the external project data depends on, still have the same content checksum.
   *
   * @return whether the data is still valid.
   * @throws IOException if there is a problem accessing these files.
   */
  public boolean canUseCachedData() {
    for (Map.Entry<String, byte[]> entry : myFileChecksums.entrySet()) {
      File file = new File(entry.getKey());
      if (!file.isAbsolute()) {
        file = new File(myRootFolderPath, file.getPath());
      }
      try {
        if (!Arrays.equals(entry.getValue(), createChecksum(file))) {
          return false;
        }
      }
      catch (Throwable e) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static byte[] createChecksum(@NotNull File file) throws IOException {
    // For files tracked by the IDE we get the content from the virtual files, otherwise we revert to io.
    byte[] data = file.exists() ? toByteArray(file) : EMPTY_BYTE_ARRAY;
    return Hashing.md5().hashBytes(data).asBytes();
  }

  public long getLastGradleSyncTimestamp() {
    return myLastGradleSyncTimestamp;
  }

  @VisibleForTesting
  @NotNull
  Map<String, byte[]> getFileChecksums() {
    return myFileChecksums;
  }
}
