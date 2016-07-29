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
package com.android.tools.idea.gradle.project;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_EXPERIMENTAL_PLUGIN_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidGradleExperimentalPluginVersionFromBuildFile;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.android.tools.idea.gradle.util.GradleUtil.isUsingExperimentalPlugin;

class AndroidGradleModelVersions {
  @NotNull private final GradleVersion myCurrent;
  @NotNull private final GradleVersion myLatest;
  private final boolean myExperimentalPlugin;

  @Nullable
  static AndroidGradleModelVersions find(@NotNull Project project) {
    Module module = getAppAndroidModule(project);
    if (module != null) {
      AndroidProject androidProject = getAndroidProject(module);
      if (androidProject != null) {
        if (isUsingExperimentalPlugin(module)) {
          // TODO: Add the plugin version to the model and get it from there directly.
          GradleVersion current = getAndroidGradleExperimentalPluginVersionFromBuildFile(project);
          if (current != null) {
            return new AndroidGradleModelVersions(current, true);
          }
        }
        else {
          GradleVersion current = GradleVersion.parse(androidProject.getModelVersion());
          return new AndroidGradleModelVersions(current, false);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Module getAppAndroidModule(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidProject androidProject = getAndroidProject(module);
      if (androidProject != null && !androidProject.isLibrary()) {
        return module;
      }
    }
    return null;
  }

  private AndroidGradleModelVersions(@NotNull GradleVersion current, boolean experimentalPlugin) {
    this(current, getLatest(experimentalPlugin), experimentalPlugin);
  }

  @NotNull
  private static GradleVersion getLatest(boolean experimentalPlugin) {
    String latest = experimentalPlugin ? GRADLE_EXPERIMENTAL_PLUGIN_LATEST_VERSION : GRADLE_PLUGIN_LATEST_VERSION;
    return GradleVersion.parse(latest);
  }

  @VisibleForTesting
  AndroidGradleModelVersions(@NotNull GradleVersion current, @NotNull GradleVersion latest, boolean experimentalPlugin) {
    myCurrent = current;
    myLatest = latest;
    myExperimentalPlugin = experimentalPlugin;
  }

  @NotNull
  GradleVersion getCurrent() {
    return myCurrent;
  }

  @NotNull
  GradleVersion getLatest() {
    return myLatest;
  }

  boolean isExperimentalPlugin() {
    return myExperimentalPlugin;
  }
}
