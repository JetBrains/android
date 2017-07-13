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

import com.android.builder.model.ProjectBuildOutput;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

/**
 * Model with all the information needed in post builds.
 */
public class PostBuildModel {
  @NotNull private final ImmutableMap<String, ProjectBuildOutput> myOutputs;

  PostBuildModel(@NotNull List<OutputBuildAction.ModuleBuildOutput> outputs) {
    ImmutableMap.Builder<String, ProjectBuildOutput> outputsBuilder = ImmutableMap.builder();
    for (OutputBuildAction.ModuleBuildOutput output : outputs) {
      outputsBuilder.put(output.getModulePath(), output.getOutput());
    }
    myOutputs = outputsBuilder.build();
  }

  @Nullable
  public ProjectBuildOutput findOutputModel(@Nullable String gradlePath) {
    return myOutputs.get(gradlePath);
  }

  @Nullable
  public ProjectBuildOutput findOutputModel(@NotNull Module module) {
    return findOutputModel(getGradlePath(module));
  }

  @Nullable
  public ProjectBuildOutput findOutputModel(@NotNull AndroidFacet facet) {
    return findOutputModel(facet.getModule());
  }
}
