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
package com.android.tools.idea.gradle.dsl.api;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * A model representing a whole project. Multiple {@link GradleBuildModel}s that are obtained via a {@link ProjectBuildModel} will present
 * the same view of the file along with any non-applied changes. Note: An exception to this is applying plugins, these are NOT persistent
 * across different models.
 */
public interface ProjectBuildModel {
  /**
   * @param project the project to create a model for.
   * @return the model for the project, or null if no build.gradle file could be found.
   */
  @Nullable
  static ProjectBuildModel get(@NotNull Project project) {
    return GradleModelProvider.get().getProjectModel(project);
  }

  /**
   * @return the {@link GradleBuildModel} for this projects root build file.
   */
  @NotNull
  GradleBuildModel getProjectBuildModel();

  /**
   * @param module the module to get the {@link GradleBuildModel} for.
   * @return the resulting model, or null if the modules build.gradle file couldn't be found.
   */
  @Nullable
  GradleBuildModel getModuleBuildModel(@NotNull Module module);

  @Nullable
  GradleBuildModel getModuleBuildModel(@NotNull File modulePath);

  /**
   * @return the settings model for this project, or null if no settings file could be found.
   */
  @Nullable
  GradleSettingsModel getProjectSettingsModel();

  /**
   * Applies changes to all {@link GradleBuildModel}s and the {@link GradleSettingsModel} that have been created by this model.
   */
  void applyChanges();

  /**
   * Resets the state of all {@link GradleBuildModel}s and the {@link GradleSettingsModel}  that have been created by this model.
   */
  void resetState();

  /**
   * Reparses all {@link GradleBuildModel}s and the {@link GradleSettingsModel}  that have been created by this model.
   */
  void reparse();
}
