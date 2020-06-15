/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler to detect and provide a more useful message and action for a incompatibility between the NDK and older versions
 * of the Android Gradle Plugin. We offer a message saying that the version of the NDK may be incompatible with AGP < 3 and a
 * link the prompts them to upgrade to the newest version of the plugin.
 */
public final class NdkToolchainMissingABIHandler extends BaseSyncErrorHandler {
  @NotNull private static final String ERROR_MESSAGE = "No toolchains found in the NDK toolchains folder for ABI with prefix: ";
  @NotNull private static final List<String> VALID_ABIS = Arrays.asList("mips64el-linux-android", "mipsel-linux-android");

  @Nullable
  @Override
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (text.startsWith(ERROR_MESSAGE)) {
      boolean valid = VALID_ABIS.stream().anyMatch(it -> text.endsWith(it));
      if (valid && !isArtifactVersionOver3dot0(getAndroidPluginArtifactModel(project))) {
        return text + "\n" +
               "This version of the NDK may be incompatible with the Android Gradle plugin version 3.0 or older.\n" +
               "Please use plugin version 3.1 or newer.";
      }
    }
    return null;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    if (!isArtifactVersionOver3dot0(getAndroidPluginArtifactModel(project))) {
      //TODO(b/130224064): need to remove check when kts fully supported
      if (!GradleUtil.hasKtsBuildFiles(project)) {
        return Collections.singletonList(new FixAndroidGradlePluginVersionHyperlink());
      }
    }
    return super.getQuickFixHyperlinks(project, text);
  }

  /**
   * Checks to see if the given model has a version above 3.0
   *
   * @param artifactModel the model to check, if null false is returned
   * @return whether the artifactModel if not null and has a version above 3.0
   */
  private static boolean isArtifactVersionOver3dot0(@Nullable ArtifactDependencyModel artifactModel) {
    if (artifactModel == null) {
      return false;
    }
    String version = artifactModel.version().toString();
    if (version == null) {
      return false;
    }
    return isVersionOver3dot0(version);
  }

  @VisibleForTesting
  static boolean isVersionOver3dot0(@NotNull String version) {
    GradleCoordinate versionOnly = GradleCoordinate.parseVersionOnly(version);
    return (versionOnly.getMajorVersion() > 3) || (versionOnly.getMajorVersion() == 3 && versionOnly.getMinorVersion() > 0);
  }

  /**
   * Attempts to find the artifact model that represents the Android Gradle Plugin in the projects root build file.
   * @param project the project to use
   * @return the artifact model if found, null otherwise
   */
  @Nullable
  private static ArtifactDependencyModel getAndroidPluginArtifactModel(@NotNull Project project) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.getOrLog(project);
    if (projectBuildModel == null) {
      return null;
    }
    GradleBuildModel rootModel = projectBuildModel.getProjectBuildModel();
    if (rootModel != null) {
      List<ArtifactDependencyModel> dependencyModels = rootModel.buildscript().dependencies().artifacts();
      return dependencyModels.stream().filter(dependency -> isAndroidPlugin(dependency)).findFirst().orElse(null);
    }
    return null;
  }

  /**
   * Checks to see if the given {@link ArtifactDependencyModel} represents the Android Gradle Plugin.
   *
   * @param artifactModel the model to check
   * @return whether or not artifactModel represents the Android Gradle Plugin
   */
  private static boolean isAndroidPlugin(@NotNull ArtifactDependencyModel artifactModel) {
    String group = artifactModel.group().toString();
    String name  = artifactModel.name().toString();
    return group != null && name != null && group.equals("com.android.tools.build") && name.equals("gradle");
  }
}
