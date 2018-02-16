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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.containers.ContainerUtilRt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleJavaProjectOpenProcessor;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.Optional;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getSettings;

class GradleSettingsCleanUpTask extends ProjectCleanUpTask {
  @Override
  void cleanUp(@NotNull Project project) {
    // auto-discovery of the gradle project located in the IDE Project.basePath can not be applied to IntelliJ IDEA
    // because IDEA still supports working with gradle projects w/o built-in gradle integration (e.g. using generated project by 'idea' gradle plugin)
    if (IdeInfo.getInstance().isAndroidStudio()) {
      @SystemIndependent String projectBasePath = project.getBasePath();
      if (projectBasePath == null) {
        return;
      }

      String externalProjectPath = ExternalSystemApiUtil.toCanonicalPath(projectBasePath);
      GradleSettings gradleSettings = (GradleSettings)getSettings(project, GradleConstants.SYSTEM_ID);
      Collection<GradleProjectSettings> projectsSettings = ContainerUtilRt.newHashSet(gradleSettings.getLinkedProjectsSettings());
      GradleProjectSettings rootProjectCandidate = null;
      if (!projectsSettings.isEmpty()) {
        Optional<GradleProjectSettings> existingRootProjectSettings = StreamEx.of(projectsSettings)
          .findFirst(projectSettings -> FileUtil.pathsEqual(externalProjectPath, projectSettings.getExternalProjectPath()));
        if (existingRootProjectSettings.isPresent()) {
          rootProjectCandidate = existingRootProjectSettings.get();
        }
      }
      if (rootProjectCandidate == null) {
        GradleJavaProjectOpenProcessor gradleProjectOpenProcessor =
          Extensions.findExtension(ProjectOpenProcessor.EXTENSION_POINT_NAME, GradleJavaProjectOpenProcessor.class);
        if (gradleProjectOpenProcessor.canOpenProject(project.getBaseDir())) {
          rootProjectCandidate = new GradleProjectSettings();
          rootProjectCandidate.setExternalProjectPath(externalProjectPath);
          projectsSettings.add(rootProjectCandidate);
          gradleSettings.setLinkedProjectsSettings(projectsSettings);
        }
      }
      if (rootProjectCandidate != null) {
        setUpGradleProjectSettings(project, rootProjectCandidate);
      }
    }
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

    // should be set when linking
    //String basePath = project.getBasePath();
    //if (basePath != null) {
    //  settings.setExternalProjectPath(basePath);
    //}
  }
}
