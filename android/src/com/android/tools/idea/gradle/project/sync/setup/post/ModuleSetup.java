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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleSetup {
  @NotNull private final Project myProject;
  @NotNull private final ModuleSetupStep[] mySetupSteps;

  public ModuleSetup(@NotNull Project project) {
    this(project, ModuleSetupStep.getExtensions());
  }

  @VisibleForTesting
  ModuleSetup(@NotNull Project project, @NotNull ModuleSetupStep... setupSteps) {
    myProject = project;
    mySetupSteps = setupSteps;
  }

  public void setUpModules(@Nullable ProgressIndicator progressIndicator) {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      for (ModuleSetupStep setupStep : mySetupSteps) {
        setupStep.setUpModule(module, progressIndicator);
      }
    }
  }
}
