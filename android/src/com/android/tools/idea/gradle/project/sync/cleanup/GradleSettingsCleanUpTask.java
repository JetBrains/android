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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

class GradleSettingsCleanUpTask extends ProjectCleanUpTask {
  @Override
  void cleanUp(@NotNull Project project) {
    GradleProjectSettings projectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
    if (projectSettings == null) {
      projectSettings = new GradleProjectSettings();
    }
    setUpGradleProjectSettings(project, projectSettings);
    GradleSettings gradleSettings = GradleSettings.getInstance(project);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private static void setUpGradleProjectSettings(@NotNull Project project, @NotNull GradleProjectSettings settings) {
    settings.setUseAutoImport(false);

    // Workaround to make integration (non-UI) tests pass.
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        settings.setGradleJvm(jdk.getName());
      }
    }

    String basePath = project.getBasePath();
    if (basePath != null) {
      settings.setExternalProjectPath(basePath);
    }
  }
}
