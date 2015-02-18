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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.util.ProxyUtil;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;
import java.util.Arrays;
import java.util.Map;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;

/**
 * The Project data that needs to be persisted for it to be possible to reload the Project without the need of calling Gradle to
 * regenerate this objects.
 */
public class AndroidGradleProjectData implements Serializable {
  @NotNull @NonNls private static final String STATE_FILE_NAME = "model_data.bin";
  private static final boolean ENABLED = !Boolean.getBoolean("studio.disable.synccache");

  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectData.class);

  /**
   * A map from module name to its data
   */
  private Map<String, ModuleData> myData = Maps.newHashMap();

  /**
   * A set of files and their MD5 that this data depends on.
   */
  private Map<String, byte[]> myFileChecksums = Maps.newHashMap();

  /**
   * The model version
   */
  private String myGradlePluginVersion = SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;

  /**
   * The last time a sync was done.
   */
  private long myLastGradleSyncTimestamp = -1L;

  private AndroidGradleProjectData() {
  }

  public static void removeFrom(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    try {
      File stateFile = getProjectStateFile(project);
      if (stateFile.isFile()) {
        FileUtil.delete(stateFile);
      }
    }
    catch (IOException e) {
      LOG.warn(String.format("Failed to remove state for project %1$s'", project.getName()));
    }
  }

  /**
   * Persists the gradle model of this project to disk.
   *
   * @param project the project to get the data from.
   */
  public static void save(@NotNull Project project) {
    if (!ENABLED) {
      return;
    }
    boolean cacheSaved = false;
    try {
      AndroidGradleProjectData data = createFrom(project);
      if (data != null) {
        File file = getProjectStateFile(project);
        FileUtil.ensureExists(file.getParentFile());
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
  static AndroidGradleProjectData createFrom(@NotNull Project project) throws IOException {
    AndroidGradleProjectData data = new AndroidGradleProjectData();
    File rootDirPath = new File(project.getBasePath());
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ModuleData moduleData = new ModuleData();

      moduleData.myName = module.getName();

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        IdeaAndroidProject ideaAndroidProject = androidFacet.getIdeaAndroidProject();
        if (ideaAndroidProject != null) {
          moduleData.myAndroidProject = ProxyUtil.reproxy(AndroidProject.class, ideaAndroidProject.getDelegate());
          moduleData.mySelectedVariant = ideaAndroidProject.getSelectedVariant().getName();
          moduleData.mySelectedTestArtifact = ideaAndroidProject.getSelectedTestArtifactName();
        }
        else {
          LOG.warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        IdeaGradleProject ideaGradleProject = gradleFacet.getGradleProject();
        if (ideaGradleProject != null) {
          data.addFileDependency(rootDirPath, ideaGradleProject.getBuildFile());
          moduleData.myIdeaGradleProject = ideaGradleProject;
        }
        else {
          LOG.warn(String.format("Trying to create project data from a not initialized project '%1$s'. Abort.", project.getName()));
          return null;
        }
      }

      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null) {
        moduleData.myJavaModel = javaFacet.getJavaModel();
      }

      if (Projects.isGradleProjectModule(module)) {
        data.addFileDependency(rootDirPath, GradleUtil.getGradleBuildFile(module));
        data.addFileDependency(rootDirPath, GradleUtil.getGradleSettingsFile(rootDirPath));
        data.addFileDependency(rootDirPath, new File(rootDirPath, SdkConstants.FN_GRADLE_PROPERTIES));
        data.addFileDependency(rootDirPath, new File(rootDirPath, SdkConstants.FN_LOCAL_PROPERTIES));
        data.addFileDependency(rootDirPath, getGradleUserSettingsFile());
      }

      data.myData.put(moduleData.myName, moduleData);
    }
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    data.myLastGradleSyncTimestamp = syncState.getLastGradleSyncTimestamp();
    return data;
  }

  @Nullable
  public static File getGradleUserSettingsFile() {
    String homePath = System.getProperty("user.home");
    if (homePath == null) {
      return null;
    }
    return new File(homePath, FileUtil.join(SdkConstants.DOT_GRADLE, SdkConstants.FN_GRADLE_PROPERTIES));
  }

  @NotNull
  private static byte[] createChecksum(@NotNull File file) throws IOException {
    // For files tracked by the IDE we get the content from the virtual files, otherwise we revert to io.
    VirtualFile vf = VfsUtil.findFileByIoFile(file, true);
    byte[] data = new byte[] {};
    if (vf != null) {
      vf.refresh(false, false);
      if (vf.exists()) {
        data = vf.contentsToByteArray();
      }
    } else if (file.exists()) {
      data = Files.toByteArray(file);
    }
    return Hashing.md5().hashBytes(data).asBytes();
  }

  /**
   * Loads the gradle model persisted on disk for the given project.
   *
   * @param project the project for which to load the data.
   * @return whether the load was successful.
   */
  public static boolean loadFromDisk(@NotNull final Project project) {
    if (!ENABLED || needsAndroidSdkSync(project)) {
      return false;
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
    return false;
  }

  private static boolean needsAndroidSdkSync(@NotNull final Project project) {
    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      final File ideSdkPath = DefaultSdks.getDefaultAndroidHome();
      if (ideSdkPath != null) {
        if (needsLPreviewPlatformReset()) {
          // reset the Android SDK home to force recreation of IDEA SDKs.
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              DefaultSdks.setDefaultAndroidHome(ideSdkPath, DefaultSdks.getDefaultJdk(), project);
            }
          });
          return true;
        }
        try {
          LocalProperties localProperties = new LocalProperties(project);
          File projectSdkPath = localProperties.getAndroidSdkPath();
          return projectSdkPath == null || !FileUtil.filesEqual(ideSdkPath, projectSdkPath);
        }
        catch (IOException ignored) {
        }
      }
      return true;
    }
    return false;
  }

  private static boolean needsLPreviewPlatformReset() {
    // Repair SDK for 'android-L'. See: https://code.google.com/p/android/issues/detail?id=72589
    // TODO: remove this at some point (it's only there to upgrade user settings for people who used 0.8.0 and 0.8.1 with 20 and 21
    // installed simultaneously)
    for (Sdk sdk : DefaultSdks.getEligibleAndroidSdks()) {
      SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
      if (additionalData instanceof AndroidSdkAdditionalData) {
        AndroidPlatform androidPlatform = ((AndroidSdkAdditionalData)additionalData).getAndroidPlatform();
        if (androidPlatform != null) {
          IAndroidTarget target = androidPlatform.getTarget();
          AndroidVersion version = target.getVersion();
          if ("L".equals(version.getApiString()) && version.getApiLevel() == 20 && version.isPreview()) {
            // This is "android-L"
            String androidJarPath = target.getPath(IAndroidTarget.ANDROID_JAR);
            File expectedPath = new File(androidJarPath);
            VirtualFile[] libraryFiles = sdk.getRootProvider().getFiles(CLASSES);
            for (VirtualFile libraryFile : libraryFiles) {
              // Match the expected path of android.jar vs. the actual path. The expected path is the one coming from SDK Manager, while
              // the actual path is the one in the IDEA SDK.
              if (FN_FRAMEWORK_LIBRARY.equals(libraryFile.getName())) {
                File actualPath = VfsUtilCore.virtualToIoFile(libraryFile);
                return !FileUtil.filesEqual(expectedPath, actualPath);
              }
            }
            // android.jar was never found.
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean doLoadFromDisk(@NotNull Project project) throws IOException, ClassNotFoundException {
    FileInputStream fin = null;
    try {
      File rootDirPath = new File(FileUtil.toSystemDependentName(project.getBasePath()));
      File dataFile = getProjectStateFile(project);
      if (!dataFile.exists()) {
        return false;
      }
      fin = new FileInputStream(dataFile);
      ObjectInputStream ois = new ObjectInputStream(fin);
      try {
        AndroidGradleProjectData data = (AndroidGradleProjectData)ois.readObject();
        if (data.validate(rootDirPath)) {
          if (data.applyTo(project)) {
            PostProjectSetupTasksExecutor.getInstance(project).onProjectRestoreFromDisk();
            return true;
          }
        }
      }
      finally {
        Closeables.close(ois, false);
      }
    }
    finally {
      Closeables.close(fin, false);
    }
    return false;
  }

  @NotNull
  private static File getProjectStateFile(@NotNull Project project) throws IOException {
    Module projectModule = Projects.findGradleProjectModule(project);
    if (projectModule != null) {
      File buildFolderPath = Projects.getBuildFolderPath(projectModule);
      if (buildFolderPath != null) {
        return new File(buildFolderPath, FileUtil.join(AndroidProject.FD_INTERMEDIATES, STATE_FILE_NAME));
      }
    }
    // TODO: Once we upgrade to Gradle 2.0, we can get the build directory from there. For now assume "build".
    return new File(VfsUtilCore.virtualToIoFile(project.getBaseDir()),
                    FileUtil.join(GradleUtil.BUILD_DIR_DEFAULT_NAME, AndroidProject.FD_INTERMEDIATES, STATE_FILE_NAME));
  }

  /**
   * Adds a dependency to the content of the given virtual file. @see addFileDependency(File, File)
   */
  private void addFileDependency(File rootDirPath, @Nullable VirtualFile vf) throws IOException {
    addFileDependency(rootDirPath, vf != null ? VfsUtilCore.virtualToIoFile(vf) : null);
  }

  /**
   * Adds a dependency to the content of the given file.
   * <p/>
   * This method saves a checksum of the content of the given file along with its location. If this file's content is later found
   * to have changed, the persisted data will be considered invalid.
   *
   * @param rootDirPath the root directory.
   * @param file        the file to add the dependency for.
   * @throws IOException if there is a problem accessing the given file.
   */
  private void addFileDependency(File rootDirPath, @Nullable File file) throws IOException {
    if (file == null) {
      return;
    }
    String key;
    if (FileUtil.isAncestor(rootDirPath, file, true)) {
      key = FileUtil.getRelativePath(rootDirPath, file);
    }
    else {
      key = file.getAbsolutePath();
    }
    myFileChecksums.put(key, createChecksum(file));
  }

  /**
   * Validates that the received data can be applied to the project at rootDir.
   * <p/>
   * This validates that all the files this model depends on, still have the same content checksum and that the gradle model version
   * is still the same.
   *
   * @param rootDir the root directory where to find the files.
   * @return whether the data is still valid.
   * @throws IOException if there is a problem accessing these files.
   */
  private boolean validate(@NotNull File rootDir) throws IOException {
    if (!myGradlePluginVersion.equals(SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION)) {
      return false;
    }

    for (Map.Entry<String, byte[]> entry : myFileChecksums.entrySet()) {
      File file = new File(entry.getKey());
      if (!file.isAbsolute()) {
        file = new File(rootDir, file.getPath());
      }
      if (!Arrays.equals(entry.getValue(), createChecksum(file))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Applies this data to the given project.
   *
   * @param project the project to apply the data to.
   */
  @VisibleForTesting
  public boolean applyTo(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ModuleData data = myData.get(module.getName());
      // If no data is found, the cache doesn't match the project structure and we should resync.
      if (data == null) {
        return false;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      String moduleFilePath = module.getModuleFilePath(); // System dependent absolute path.
      if (androidFacet != null) {
        if (data.myAndroidProject != null) {
          File moduleFile = new File(moduleFilePath);
          assert moduleFile.getParent() != null : moduleFile.getPath();
          File moduleRootDirPath = moduleFile.getParentFile();
          IdeaAndroidProject ideaAndroidProject =
            new IdeaAndroidProject(GradleConstants.SYSTEM_ID,
                                   module.getName(),
                                   moduleRootDirPath,
                                   data.myAndroidProject,
                                   data.mySelectedVariant,
                                   data.mySelectedTestArtifact);
          androidFacet.setIdeaAndroidProject(ideaAndroidProject);
        }
        else {
          return false;
        }
      }

      AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
      if (gradleFacet != null) {
        gradleFacet.setGradleProject(data.myIdeaGradleProject);
      }

      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null && data.myJavaModel != null) {
        javaFacet.setJavaModel(data.myJavaModel);
      }
    }
    GradleSyncState.getInstance(project).syncSkipped(myLastGradleSyncTimestamp);
    return true;
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
        Closeables.close(oos, false);
      }
    }
    finally {
      Closeables.close(fos, false);
    }
  }

  @VisibleForTesting
  Map<String, ModuleData> getModuleData() {
    return myData;
  }

  @VisibleForTesting
  Map<String, byte[]> getFileChecksums() {
    return myFileChecksums;
  }

  /**
   * The persistent data to store per project Module.
   */
  static class ModuleData implements Serializable {
    public String myName;
    public IdeaGradleProject myIdeaGradleProject;
    public AndroidProject myAndroidProject;
    public String mySelectedVariant;
    public String mySelectedTestArtifact;
    public JavaModel myJavaModel;
  }
}
