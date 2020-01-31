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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final ProjectSetupStep[] mySetupSteps;

  public ProjectSetup(@NotNull Project project) {
    this(project, ProjectSetupStep.getExtensions());
  }

  @VisibleForTesting
  ProjectSetup(@NotNull Project project, @NotNull ProjectSetupStep... setupSteps) {
    myProject = project;
    mySetupSteps = setupSteps;
  }

  public void setUpProject(boolean syncFailed) {
    for (ProjectSetupStep step : mySetupSteps) {
      if (!syncFailed || step.invokeOnFailedSync()) {
        step.setUpProject(myProject);
      }
    }
  }
}
