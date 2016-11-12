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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.module.Module;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION;
import static com.android.builder.model.AndroidProject.GENERATION_COMPONENT;
import static com.android.tools.idea.gradle.dsl.model.values.GradleValue.getValues;

public enum AndroidPluginGeneration {
  ORIGINAL {
    @Override
    @NotNull
    protected String getArtifactId() {
      return "gradle";
    }

    @Override
    @NotNull
    protected String getApplicationPluginId() {
      return "com.android.application";
    }

    @Override
    @NotNull
    protected String getLibraryPluginId() {
      return "com.android.library";
    }

    @Override
    @NotNull
    public String getRecommendedVersion() {
      return GradleUtil.getLatestKnownPluginVersion(GradleUtil.PluginType.STANDARD);
    }

    @Override
    @NotNull
    public String getDescription() {
      return "Android Gradle plugin";
    }
  },

  // This is the "experimental" plugin.
  COMPONENT {
    @Override
    @NotNull
    protected String getArtifactId() {
      return "gradle-experimental";
    }

    @Override
    @NotNull
    protected String getApplicationPluginId() {
      return "com.android.model.application";
    }

    @Override
    @NotNull
    protected String getLibraryPluginId() {
      return "com.android.model.library";
    }

    @Override
    @NotNull
    public String getRecommendedVersion() {
      return GRADLE_EXPERIMENTAL_PLUGIN_RECOMMENDED_VERSION;
    }

    @Override
    @NotNull
    public String getDescription() {
      return "Android Gradle \"experimental\" plugin";
    }
  };

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
  protected abstract String getApplicationPluginId();

  protected abstract String getLibraryPluginId();

  public boolean isAndroidPlugin(@NotNull String artifactId, @Nullable String groupId) {
    return getArtifactId().equals(artifactId) && getGroupId().equals(groupId);
  }

  @NotNull
  protected abstract String getArtifactId();

  @Nullable
  public static AndroidPluginGeneration find(@NotNull String artifactId, @Nullable String groupId) {
    for (AndroidPluginGeneration generation : values()) {
      if (generation.isAndroidPlugin(artifactId, groupId)) {
        return generation;
      }
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
        // only true for experimental plugin 0.6.0-betaX (or whenever the getPluginGeneration() was added) or later.
        return gradleModel.getAndroidProject().getPluginGeneration() == GENERATION_COMPONENT ? COMPONENT : ORIGINAL;
      }
      catch (UnsupportedMethodException t) {
        // happens for 2.0.0-alphaX or earlier stable version plugins and 0.6.0-alphax or earlier experimental plugin versions.
      }
    }

    // Now look at the applied plugins in the build.gradle file.
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      List<String> appliedPlugins = getValues(buildModel.appliedPlugins());
      for (AndroidPluginGeneration generation : values()) {
        if (appliedPlugins.contains(generation.getApplicationPluginId()) || appliedPlugins.contains(generation.getLibraryPluginId())) {
          return generation;
        }
      }
    }

    return null;
  }

  @NotNull
  public abstract String getRecommendedVersion();

  @NotNull
  public abstract String getDescription();
}
