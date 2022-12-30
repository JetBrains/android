/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.model.impl.BuildFolderPaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;

public final class ModelConverter {
  /**
   * Set map from project path to build directory for all modules.
   * It will be used to check if an android library is a sub-module that wraps a local aar.
   */
  @NotNull
  public static BuildFolderPaths populateModuleBuildDirs(@NotNull BuildController controller) {
    IdeaProject rootIdeaProject = controller.findModel(IdeaProject.class);
    if (rootIdeaProject == null) {
      return new BuildFolderPaths();
    }
    BuildFolderPaths buildFolderPaths = new BuildFolderPaths();
    // Set root build id.
    for (IdeaModule ideaModule : rootIdeaProject.getChildren()) {
      GradleProject gradleProject = ideaModule.getGradleProject();
      if (gradleProject != null) {
        File buildRootDirectory = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir();
        String rootBuildId = buildRootDirectory.getPath();
        buildFolderPaths.setRootBuildId(rootBuildId);
        buildFolderPaths.setBuildRootDirectory(buildRootDirectory);
        break;
      }
    }

    // Set build folder for root and included projects.
    List<IdeaProject> ideaProjects = new ArrayList<>();
    ideaProjects.add(rootIdeaProject);
    DomainObjectSet<? extends GradleBuild> includedBuilds = controller.getBuildModel().getIncludedBuilds();
    for (GradleBuild includedBuild : includedBuilds) {
      IdeaProject ideaProject = controller.getModel(includedBuild, IdeaProject.class);
      assert ideaProject != null;
      ideaProjects.add(ideaProject);
    }

    for (IdeaProject ideaProject : ideaProjects) {
      for (IdeaModule ideaModule : ideaProject.getChildren()) {
        GradleProject gradleProject = ideaModule.getGradleProject();
        if (gradleProject != null) {
          try {
            String buildId = gradleProject.getProjectIdentifier().getBuildIdentifier().getRootDir().getPath();
            buildFolderPaths.addBuildFolderMapping(buildId, gradleProject.getPath(), gradleProject.getBuildDirectory());
          }
          catch (UnsupportedOperationException exception) {
            // getBuildDirectory is not available for Gradle older than 2.0.
            // For older versions of gradle, there's no way to get build directory.
          }
        }
      }
    }
    return buildFolderPaths;
  }
}
