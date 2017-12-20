/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs Android Gradle project with the persisted project data on startup.
 */
public class AndroidGradleProjectStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
    if (gradleProjectInfo.isBuildWithGradle() && !gradleProjectInfo.isImportedProject()) {
      // http://b/62543184
      // If the project was created with the "New Project" wizard or imported, there is no need to sync again.
      // This code path should only be executed when:
      // 1. Opening an existing project from the list of "recent projects" in the "Welcome" page
      // 2. Reopening the IDE and automatically reloading the current open project
      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectLoaded();
      request.useCachedGradleModels = true;

      GradleSyncInvoker.getInstance().requestProjectSync(project, request);
    }
  }
}
