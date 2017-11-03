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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

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

  public void setUpProject(@Nullable ProgressIndicator progressIndicator, boolean syncFailed) {
    Runnable invokeProjectSetupStepsTask = () -> {
      for (ProjectSetupStep step : mySetupSteps) {
        if (!syncFailed || step.invokeOnFailedSync()) {
          step.setUpProject(myProject, progressIndicator);
        }
      }
    };

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      invokeProjectSetupStepsTask.run();
      return;
    }

    invokeLaterIfNeeded(() -> ApplicationManager.getApplication().runWriteAction(invokeProjectSetupStepsTask));
  }
}
