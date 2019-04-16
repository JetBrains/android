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
package com.android.tools.idea.gradle.plugin;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.ide.common.repository.MavenRepositories.getHighestInstalledVersion;

public class AndroidPluginGeneration {
  public static final AndroidPluginGeneration ORIGINAL = new AndroidPluginGeneration();

  /**
   * Indicates whether the given collection of plugin IDs contains the Android "app" plugin ID.
   *
   * @param pluginIds the given collection of plugin IDs.
   * @return {@code true} if the given collection of plugin IDs contains the Android "app" plugin ID; {@code false} otherwise.
   */
  public boolean isApplicationPluginIdIn(@NotNull Collection<String> pluginIds) {
    return pluginIds.contains(getApplicationPluginId());
  }

  @NotNull
  protected String getApplicationPluginId() {
    return "com.android.application";
  }

  @NotNull
  protected String getLibraryPluginId() {
    return "com.android.library";
  }


  public boolean isAndroidPlugin(@NotNull String artifactId, @Nullable String groupId) {
    return getArtifactId().equals(artifactId) && getGroupId().equals(groupId);
  }

  @NotNull
  public String getArtifactId() {
    return "gradle";
  }

  @Nullable
  public static AndroidPluginGeneration find(@NotNull String artifactId, @Nullable String groupId) {
    if (ORIGINAL.isAndroidPlugin(artifactId, groupId)) {
      return ORIGINAL;
    }
    return null;
  }

  @NotNull
  public static String getGroupId() {
    return "com.android.tools.build";
  }

  @Nullable
  public static AndroidPluginGeneration find(@NotNull Module module) {
    AndroidModuleModel gradleModel = AndroidModuleModel.get(module);
    if (gradleModel != null) {
      try {
        gradleModel.getAndroidProject().getPluginGeneration();
        return ORIGINAL;
      }
      catch (UnsupportedOperationException t) {
        // happens for 2.0.0-alphaX or earlier stable version plugins and 0.6.0-alphax or earlier experimental plugin versions.
      }
    }

    // Now look at the applied plugins in the build.gradle file.
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      List<String> appliedPlugins = PluginModel.extractNames(buildModel.plugins());
      if (appliedPlugins.contains(ORIGINAL.getApplicationPluginId()) || appliedPlugins.contains(ORIGINAL.getLibraryPluginId())) {
        return ORIGINAL;
      }
    }

    return null;
  }

  @NotNull
  public String getLatestKnownVersion() {
    return getLatestKnownVersion(this);
  }

  @NotNull
  protected String getLatestKnownVersion(@NotNull AndroidPluginGeneration generation) {
    String artifactId = generation.getArtifactId();
    FileOp fileOp = FileOpUtils.create();

    List<File> repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    Optional<GradleCoordinate> highestValueCoordinate = repoPaths.stream()
      .map(repoPath -> getHighestInstalledVersion(getGroupId(), artifactId, repoPath, null /* filter */, true /* allow preview */, fileOp))
      .filter(Objects::nonNull)
      .max(COMPARE_PLUS_HIGHER);

    if (!highestValueCoordinate.isPresent()) {
      // If there is no embedded repo, then use the last known version from SdkConstants
      // TODO: revisit this when tests are running with the latest (source) build.
      String version = generation.getRecommendedVersion();
      getLog().info("'" + artifactId + "' plugin missing from the offline Maven repo, will use default " + version);
      return version;
    }

    return highestValueCoordinate.get().getRevision();
  }

  @NotNull
  protected String getRecommendedVersion() {
    return GRADLE_PLUGIN_RECOMMENDED_VERSION;
  }


  @NotNull
  private Logger getLog() {
    return Logger.getInstance(getClass());
  }

  @NotNull
  public String getDescription() {
    return "Android Gradle plugin";
  }
}
