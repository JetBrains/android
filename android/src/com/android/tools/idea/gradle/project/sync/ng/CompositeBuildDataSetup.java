/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.settings.CompositeDefinitionSource;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;

/**
 * Set up composite build data in GradleProjectSettings.
 * This is the counterpart of CompositeBuildDataService in old sync.
 */
public class CompositeBuildDataSetup {
  public void setupCompositeBuildData(@NotNull CachedProjectModels projectModels, @NotNull Project project) {
    doSetupCompositeBuild(projectModels.getBuildParticipants(), project);
  }

  public void setupCompositeBuildData(@NotNull SyncProjectModels projectModels,
                                      @NotNull CachedProjectModels cache,
                                      @NotNull Project project) {
    // Key: build id of included build
    Map<String, BuildParticipant> compositeParticipants = new HashMap<>();
    for (SyncModuleModels moduleModels : projectModels.getSyncModuleModels()) {
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      BuildIdentifier moduleBuildId = moduleModels.getBuildId();
      if (gradleProject == null || moduleBuildId == projectModels.getRootBuildId()) {
        continue;
      }
      try {
        String buildPath = toCanonicalPath(moduleBuildId.getRootDir().getCanonicalPath());
        BuildParticipant buildParticipant = compositeParticipants.computeIfAbsent(buildPath, p -> {
          BuildParticipant participant = new BuildParticipant();
          cache.addBuildParticipant(participant);
          return participant;
        });
        buildParticipant.setRootPath(buildPath);
        buildParticipant.getProjects().add(toCanonicalPath(gradleProject.getProjectDirectory().getCanonicalPath()));
      }
      catch (IOException e) {
        Logger.getInstance(CompositeBuildDataSetup.class).warn("Fails to construct the canonical path for module", e);
      }
    }
    doSetupCompositeBuild(new ArrayList<>(compositeParticipants.values()), project);
  }

  private static void doSetupCompositeBuild(@NotNull List<BuildParticipant> buildParticipants, @NotNull Project project) {
    String projectPath = project.getBasePath();
    if (projectPath == null) {
      return;
    }
    GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath);
    if (projectSettings == null) {
      return;
    }

    GradleProjectSettings.CompositeBuild compositeBuild = new GradleProjectSettings.CompositeBuild();
    compositeBuild.setCompositeDefinitionSource(CompositeDefinitionSource.SCRIPT);

    compositeBuild.setCompositeParticipants(buildParticipants);
    projectSettings.setCompositeBuild(compositeBuild);
  }
}

