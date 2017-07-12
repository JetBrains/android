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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;

/**
 * {@link BuildAction} to be run when building project pre deploy. It returns the {@link ProjectBuildOutput}
 * model for each one of the needed modules.
 *
 * <p> These models are used for obtaining information not known in sync time. e.g. built apks when using config splits.
 */
public class OutputBuildAction implements BuildAction<ImmutableMap<String, ProjectBuildOutput>>, Serializable {
  @NotNull private final ImmutableCollection<String> myGradlePaths;

  public OutputBuildAction(@NotNull Collection<String> moduleGradlePaths) {
    myGradlePaths = ImmutableSet.copyOf(moduleGradlePaths);
  }

  @Override
  public ImmutableMap<String, ProjectBuildOutput> execute(BuildController controller) {
    ImmutableMap.Builder<String, ProjectBuildOutput> outputsBuilder = ImmutableMap.builder();

    if (!myGradlePaths.isEmpty()) {
      BasicGradleProject rootProject = controller.getBuildModel().getRootProject();
      GradleProject root = controller.findModel(rootProject, GradleProject.class);

      for (String path : myGradlePaths) {
        GradleProject module = root.findByPath(path);
        ProjectBuildOutput model = controller.findModel(module, ProjectBuildOutput.class);
        if (model != null) {
          outputsBuilder.put(path, model);
        }
      }
    }

    return outputsBuilder.build();
  }
}