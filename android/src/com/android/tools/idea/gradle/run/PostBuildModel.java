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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;

/**
 * Model with all the information needed in post builds.
 */
public class PostBuildModel {
  @NotNull private final ImmutableMap<String, ProjectBuildOutput> myOutputs;

  PostBuildModel(@NotNull ImmutableMap<String, ProjectBuildOutput> outputs) {
    myOutputs = outputs;
  }

  @Nullable
  public ProjectBuildOutput getOutputModelForGradlePath(@Nullable String gradlePath) {
    return myOutputs.get(gradlePath);
  }

  @Nullable
  public ProjectBuildOutput getOutputModelForModule(@NotNull Module module) {
    return getOutputModelForGradlePath(getGradlePath(module));
  }

  @Nullable
  public ProjectBuildOutput getOutputModelForFacet(@NotNull AndroidFacet facet) {
    return getOutputModelForModule(facet.getModule());
  }
}
