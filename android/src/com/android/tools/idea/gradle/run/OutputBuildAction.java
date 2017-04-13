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
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * {@link BuildAction} to be run when building project pre deploy. It returns the {@link ProjectBuildOutput}
 * model with the just built apks.
 */
public class OutputBuildAction implements BuildAction<ProjectBuildOutput>, Serializable {
  @Nullable private final String myGradlePath;

  public OutputBuildAction(@Nullable String moduleGradlePath) {
    myGradlePath = moduleGradlePath;
  }

  @Override
  public ProjectBuildOutput execute(BuildController controller) {
    BasicGradleProject rootProject = controller.getBuildModel().getRootProject();
    GradleProject root = controller.findModel(rootProject, GradleProject.class);
    GradleProject module = root.findByPath(myGradlePath);

    return controller.findModel(module, ProjectBuildOutput.class);
  }
}