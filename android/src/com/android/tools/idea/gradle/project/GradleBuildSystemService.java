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
import com.android.tools.idea.templates.GradleFilePsiMerger;
import com.android.tools.idea.templates.GradleFileSimpleMerger;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

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
    if (project.isInitialized()) {
      BuildVariantView.getInstance(project).projectImportStarted();
      // TODO Can this be called directly by the user? If yes, then need to add something to tell what triggered sync
      syncAndGenerateSources(project, TRIGGER_PROJECT_MODIFIED);
    }
    else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
        if (!GradleProjectInfo.getInstance(project).isNewOrImportedProject()) {
          // http://b/62543184
          // If the project was created with the "New Project" wizard, there is no need to sync again.
          syncAndGenerateSources(project, TRIGGER_PROJECT_LOADED);
        }
      });
    }
  }

  private static void syncAndGenerateSources(@NotNull Project project, GradleSyncStats.Trigger trigger) {
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, trigger, null);
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
                                @NotNull Project project,
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
