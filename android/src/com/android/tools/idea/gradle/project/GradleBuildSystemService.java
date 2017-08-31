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
package com.android.tools.idea.gradle.project;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.templates.GradleFilePsiMerger;
import com.android.tools.idea.templates.GradleFileSimpleMerger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class GradleBuildSystemService implements BuildSystemService {

  private Project project;

  public GradleBuildSystemService(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public boolean isApplicable() {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }

  /**
   * @param artifact The dependency artifact without version.
   *                 This method will add the ":+" to the given artifact.
   *                 For Guava, for example: the artifact coordinate will not include the version:
   *                 com.google.guava:guava
   *                 and this method will add "+" as the version of the dependency to add.
   */
  @Override
  public void addDependency(@NotNull Module module, @NotNull String artifact) {
    GradleDependencyManager manager = GradleDependencyManager.getInstance(module.getProject());
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(artifact + ":+");
    manager.ensureLibraryIsIncluded(module, Collections.singletonList(coordinate), null);
  }

  @Override
  public String mergeBuildFiles(@NotNull String dependencies,
                                @NotNull String destinationContents,
                                @Nullable String supportLibVersionFilter) {
    if (project.isInitialized()) {
      return GradleFilePsiMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter);
    }
    else {
      return GradleFileSimpleMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter);
    }
  }

  @Override
  public List<AndroidSourceSet> getSourceSets(@NotNull AndroidFacet facet, @Nullable VirtualFile targetDirectory) {
    return GradleAndroidProjectPaths.getSourceSets(facet, targetDirectory);
  }
}
