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
package com.android.tools.idea.gradle.dsl.api.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DependenciesModel {
  @NotNull
  List<DependencyModel> all();

  @NotNull
  List<ArtifactDependencyModel> artifacts(@NotNull String configurationName);

  @NotNull
  List<ArtifactDependencyModel> artifacts();

  @NotNull
  DependenciesModel addArtifact(@NotNull String configurationName, @NotNull String compactNoation);

  boolean containsArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency);

  @NotNull
  DependenciesModel addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency);

  @NotNull
  DependenciesModel addArtifact(@NotNull String configurationName,
                                @NotNull ArtifactDependencySpec dependency,
                                @NotNull List<ArtifactDependencySpec> exculdes);

  @NotNull
  List<ModuleDependencyModel> modules();

  @NotNull
  DependenciesModel addModule(@NotNull String configuationName, @NotNull String path);

  @NotNull
  DependenciesModel addModule(@NotNull String configuationName, @NotNull String path, @Nullable String config);

  @NotNull
  List<FileTreeDependencyModel> fileTrees();

  @NotNull
  DependenciesModel addFileTree(@NotNull String configurationName, @NotNull String dir);

  @NotNull
  DependenciesModel addFileTree(@NotNull String configurationName,
                                @NotNull String dir,
                                @Nullable List<String> includes,
                                @Nullable List<String> excludes);


  @NotNull
  List<FileDependencyModel> files();

  @NotNull
  DependenciesModel addFile(@NotNull String configurationName, @NotNull String file);

  @NotNull
  DependenciesModel remove(@NotNull DependencyModel dependency);
}
