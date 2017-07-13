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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * {@link BuildAction} to be run when building project pre deploy. It returns the {@link ProjectBuildOutput}
 * model for each one of the needed modules.
 *
 * <p> These models are used for obtaining information not known in sync time. e.g. built apks when using config splits.
 */
public class OutputBuildAction implements BuildAction<List<OutputBuildAction.ModuleBuildOutput>>, Serializable {
  @NotNull private final ImmutableCollection<String> myGradlePaths;

  public OutputBuildAction(@NotNull Collection<String> moduleGradlePaths) {
    myGradlePaths = ImmutableSet.copyOf(moduleGradlePaths);
  }

  @Override
  public List<OutputBuildAction.ModuleBuildOutput> execute(BuildController controller) {
    ImmutableList.Builder<OutputBuildAction.ModuleBuildOutput> outputsBuilder = ImmutableList.builder();

    if (!myGradlePaths.isEmpty()) {
      BasicGradleProject rootProject = controller.getBuildModel().getRootProject();
      GradleProject root = controller.findModel(rootProject, GradleProject.class);

      for (String path : myGradlePaths) {
        GradleProject module = root.findByPath(path);
        ProjectBuildOutput output = controller.findModel(module, ProjectBuildOutput.class);
        if (output != null) {
          outputsBuilder.add(new ModuleBuildOutput(path, output));
        }
      }
    }

    return outputsBuilder.build();
  }

  public static class ModuleBuildOutput implements Serializable {
    @NotNull private final String myModulePath;
    @NotNull private final ProjectBuildOutput myOutput;

    @VisibleForTesting
    public ModuleBuildOutput(@NotNull String modulePath, @NotNull ProjectBuildOutput output) {
      myModulePath = modulePath;
      myOutput = output;
    }

    @NotNull
    public String getModulePath() {
      return myModulePath;
    }

    @NotNull
    public ProjectBuildOutput getOutput() {
      return myOutput;
    }
  }
}