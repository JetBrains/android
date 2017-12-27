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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

/**
 * Model with all the information needed in post builds.
 * This model is built from the result of {@link OutputBuildAction} and should be used instead of the inner classes of the action.
 */
public class PostBuildModel {
  @NotNull private final OutputBuildAction.PostBuildProjectModels myPostBuildProjectModels;

  public PostBuildModel(@NotNull OutputBuildAction.PostBuildProjectModels outputs) {
    myPostBuildProjectModels = outputs;
  }

  @Nullable
  private <T> T findOutputModel(@Nullable String gradlePath, @NotNull Class<T> modelType) {
    if (gradlePath == null) {
      return null;
    }

    OutputBuildAction.PostBuildModuleModels postBuildModuleModels = myPostBuildProjectModels.getModels(gradlePath);
    return postBuildModuleModels == null ? null : postBuildModuleModels.findModel(modelType);
  }

  @Nullable
  private  <T> T findOutputModel(@NotNull Module module, @NotNull Class<T> modelType) {
    return findOutputModel(getGradlePath(module), modelType);
  }

  @Nullable
  private  <T> T findOutputModel(@NotNull AndroidFacet facet, @NotNull Class<T> modelType) {
    return findOutputModel(facet.getModule(), modelType);
  }

  @Nullable
  public ProjectBuildOutput findProjectBuildOutput(@NotNull String gradlePath) {
    return findOutputModel(gradlePath, ProjectBuildOutput.class);
  }

  @Nullable
  public InstantAppProjectBuildOutput findInstantAppProjectBuildOutput(@NotNull String gradlePath) {
    return findOutputModel(gradlePath, InstantAppProjectBuildOutput.class);
  }

  @Nullable
  public ProjectBuildOutput findProjectBuildOutput(@NotNull Module module) {
    return findOutputModel(module, ProjectBuildOutput.class);
  }

  @Nullable
  public InstantAppProjectBuildOutput findInstantAppProjectBuildOutput(@NotNull Module module) {
    return findOutputModel(module, InstantAppProjectBuildOutput.class);
  }

  @Nullable
  public ProjectBuildOutput findProjectBuildOutput(@NotNull AndroidFacet facet) {
    return findOutputModel(facet, ProjectBuildOutput.class);
  }

  @Nullable
  public InstantAppProjectBuildOutput findInstantAppProjectBuildOutput(@NotNull AndroidFacet facet) {
    return findOutputModel(facet, InstantAppProjectBuildOutput.class);
  }
}
