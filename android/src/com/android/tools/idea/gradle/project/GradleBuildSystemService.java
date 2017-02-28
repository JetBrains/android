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
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.project.BuildSystemService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleBuildSystemService extends BuildSystemService {
  @Override
  public boolean isApplicable(@NotNull Project project) {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }

  @Override
  public void buildProject(@NotNull Project project) {
    GradleProjectBuilder.getInstance(project).compileJava();
  }

  @Override
  public void syncProject(@NotNull Project project) {
    BuildVariantView.getInstance(project).projectImportStarted();
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, null);
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
  public List<AndroidSourceSet> getSourceSets(@NotNull AndroidFacet facet, @Nullable VirtualFile targetDirectory) {
    return GradleAndroidProjectPaths.getSourceSets(facet, targetDirectory);
  }
}
