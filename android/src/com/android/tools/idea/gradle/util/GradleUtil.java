/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.text.StringUtil.trimLeading;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetConfiguration;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.utils.BuildScriptUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Utilities related to Gradle.
 */
public final class GradleUtil {
  public static final ProjectSystemId GRADLE_SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NonNls public static final String BUILD_DIR_DEFAULT_NAME = "build";
  @NonNls public static final String GRADLEW_PROPERTIES_PATH = join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES);

  private GradleUtil() {
  }

  /**
   * Returns the build.gradle file in the given module. This method first checks if the Gradle model has the path of the build.gradle
   * file for the given module. If it doesn't find it, it tries to find a build.gradle inside the module's root directory (folder with .iml
   * file). If it is a root module without sources, it looks inside project's base path before looking in the module's root directory.
   *
   * @param module the given module.
   * @return the build.gradle file in the given module, or {@code null} if it cannot be found.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull Module module) {
    GradleModuleModel moduleModel = getGradleModuleModel(module);
    if (moduleModel != null) {
      return moduleModel.getBuildFile();
    }

    File moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module);
    return moduleRoot != null ? getGradleBuildFile(moduleRoot) : null;
  }

  /**
   * Returns the virtual file representing a build.gradle or build.gradle.kts file in the directory at the given
   * parentDir. build.gradle.kts is only returned when build.gradle doesn't exist and build.gradle.kts exists.
   *
   * __Note__: Do __not__ use this method unless you have to, use {@link #getGradleBuildFile(Module)} instead.
   * This will return the actual build script that is used by Gradle rather than just guessing its location.
   *
   * __Note__: There is a {@link File} implementation of this method {@link BuildScriptUtil#findGradleBuildFile(File)}.
   * Prefer working with {@link VirtualFile}s if possible as these are more compatible with IDEAs testing infrastructure.
   *
   */
  @Nullable
  public static VirtualFile findGradleBuildFile(@NotNull VirtualFile parentDir) {
    return findFileWithNames(parentDir, FN_BUILD_GRADLE, FN_BUILD_GRADLE_KTS);
  }

  /**
   * Returns the virtual file representing a settings.gradle or settings.gradle.kts file in the directory at the given
   * parentDir. settings.gradle.kts is only returned when settings.gradle doesn't exist and settings.gradle.kts exists.
   *
   * __Note__: There is a {@link File} implementation of this method {@link BuildScriptUtil#findGradleSettingsFile(File)}.
   * Prefer working with {@link VirtualFile}s if possible as these are more compatible with IDEAs testing infrastructure.
   */
  @Nullable
  public static VirtualFile findGradleSettingsFile(@NotNull VirtualFile parentDir) {
    return findFileWithNames(parentDir, FN_SETTINGS_GRADLE, FN_SETTINGS_GRADLE_KTS);
  }

  /**
   * Finds and returns a file that exists as a child of the parentDir with one of the given names. This method will search for the
   * names in order and will return as soon as one is found.
   */
  @Nullable
  private static VirtualFile findFileWithNames(@NotNull VirtualFile parentDir, @NotNull String...names) {
    for (String name : names) {
      VirtualFile file = parentDir.findChild(name);
      if (file != null && !file.isDirectory()) {
        return file;
      }
    }
    return null;
  }

  @Nullable
  public static GradleModuleModel getGradleModuleModel(Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return null;
    }
    return gradleFacet.getGradleModuleModel();
  }

  /**
   * Returns the build.gradle file that is expected right in the directory at the given path. For example, if the directory path is
   * '~/myProject/myModule', this method will look for the file '~/myProject/myModule/build.gradle'. This method does not cause a VFS
   * refresh of the file, this should be done by the caller if it is likely that the file has just been created on disk.
   * <p>
   * <b>Note:</b> Only use this method if you do <b>not</b> have a reference to a {@link Module}. Otherwise use
   * {@link #getGradleBuildFile(Module)}.
   * </p>
   *
   * @param dirPath the given directory path.
   * @return the build.gradle file in the directory at the given path, or {@code null} if there is no build.gradle file in the given
   * directory path.
   */
  @Nullable
  public static VirtualFile getGradleBuildFile(@NotNull File dirPath) {
    File gradleBuildFilePath = BuildScriptUtil.findGradleBuildFile(dirPath);
    VirtualFile result = findFileByIoFile(gradleBuildFilePath, false);
    return (result != null && result.isValid()) ? result : null;
  }

  /**
   * Get last known AGP version from a project. It can be null if it has not been setup.
   */
  @Nullable
  public static String getLastKnownAndroidGradlePluginVersion(@NotNull Project project) {
    for (Module module : ProjectFacetManager.getInstance(project).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      GradleFacetConfiguration configuration = gradleFacet.getConfiguration();
      String version = configuration.LAST_KNOWN_AGP_VERSION;
      if (version != null) {
        // All versions should be the same, return version from first module found
        return version;
      }
    }
    return null;
  }

  /**
   * Get last successful AGP version from a project. It can be null if sync has never been successful.
   */
  @Nullable
  public static String getLastSuccessfulAndroidGradlePluginVersion(@NotNull Project project) {
    for (Module module : ProjectFacetManager.getInstance(project).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet == null) {
        continue;
      }
      GradleFacetConfiguration configuration = gradleFacet.getConfiguration();
      String version = configuration.LAST_SUCCESSFUL_SYNC_AGP_VERSION;
      if (version != null) {
        // All versions should be the same, return version from first module found
        return version;
      }
    }
    return null;
  }

  /**
   * Computes a library name intended for display purposes; names may not be unique
   * (and separator is always ":"). It will only show the artifact id, if that id contains slashes, otherwise
   * it will include the last component of the group id (unless identical to the artifact id).
   * <p>
   * E.g.
   * com.android.support.test.espresso:espresso-core:3.0.1@aar -> espresso-core:3.0.1
   * android.arch.lifecycle:extensions:1.0.0-beta1@aar -> lifecycle:extensions:1.0.0-beta1
   * com.google.guava:guava:11.0.2@jar -> guava:11.0.2
   */
  @NotNull
  public static String getDependencyDisplayName(@NotNull String artifactAddress) {
    GradleCoordinate coordinates = GradleCoordinate.parseCoordinateString(artifactAddress);
    if (coordinates != null) {
      String name = coordinates.getArtifactId();

      // For something like android.arch.lifecycle:runtime, instead of just showing "runtime",
      // we show "lifecycle:runtime"
      if (!name.contains("-")) {
        String groupId = coordinates.getGroupId();
        int index = groupId.lastIndexOf('.'); // okay if it doesn't exist
        String groupSuffix = groupId.substring(index + 1);
        if (!groupSuffix.equals(name)) { // e.g. for com.google.guava:guava we'd end up with "guava:guava"
          name = groupSuffix + ":" + name;
        }
      }

      Version version = coordinates.getLowerBoundVersion();
      if (version != null && !"unspecified".equals(version.toString())) {
        name += ":" + version;
      }
      return name;
    }
    return trimLeading(artifactAddress, ':');
  }
}
