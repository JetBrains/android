/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

public interface GradleBuildModel extends GradleFileModel {
  @Nullable
  static GradleBuildModel get(@NotNull Project project) {
    return GradleModelProvider.get().getBuildModel(project);
  }

  @Nullable
  static GradleBuildModel get(@NotNull Module module) {
    return GradleModelProvider.get().getBuildModel(module);
  }

  @NotNull
  static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return GradleModelProvider.get().parseBuildFile(file, project);
  }

  @NotNull
  static GradleBuildModel parseBuildFile(@NotNull VirtualFile file,
                                         @NotNull Project project,
                                         @NotNull String moduleName) {
    return GradleModelProvider.get().parseBuildFile(file, project, moduleName);
  }

  @NotNull
  List<GradleNotNullValue<String>> appliedPlugins();

  @NotNull
  GradleBuildModel applyPlugin(@NotNull String plugin);

  @NotNull
  GradleBuildModel removePlugin(@NotNull String plugin);

  @Nullable
  AndroidModel android();

  @NotNull
  BuildScriptModel buildscript();

  @NotNull
  DependenciesModel dependencies();

  @NotNull
  ExtModel ext();

  @NotNull
  JavaModel java();

  @NotNull
  RepositoriesModel repositories();

  /**
   * @return the models for files that are used by this GradleBuildModel.
   */
  @NotNull
  Set<GradleFileModel> getInvolvedFiles();

  /**
   * Removes repository property.
   */
  @TestOnly
  void removeRepositoriesBlocks();
}
