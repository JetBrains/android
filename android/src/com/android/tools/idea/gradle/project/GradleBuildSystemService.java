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
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.templates.GradleFilePsiMerger;
import com.android.tools.idea.templates.GradleFileSimpleMerger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
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

  @Override
  public void buildProject() {
    GradleProjectBuilder.getInstance(project).compileJava();
  }

  @Override
  @NotNull
  public ListenableFuture<SyncResult> syncProject(@NotNull SyncReason reason, boolean requireSourceGeneration) {
    SettableFuture<SyncResult> syncResult = SettableFuture.create();

    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      syncResult.setException(new RuntimeException("A sync was requested while one is already in progress. Use"
                                                   + "GradleSyncState.isSyncInProgress to detect this scenario."));

    } else if (project.isInitialized()) {
      BuildVariantView.getInstance(project).projectImportStarted();
      requestSync(reason, requireSourceGeneration, syncResult);

    } else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
        if (!GradleProjectInfo.getInstance(project).isNewOrImportedProject()) {
          // http://b/62543184
          // If the project was created with the "New Project" wizard, there is no need to sync again.
          requestSync(reason, requireSourceGeneration, syncResult);
        } else {
          syncResult.set(SyncResult.SKIPPED);
        }
      });
    }

    return syncResult;
  }

  @Contract(pure = true)
  private static GradleSyncStats.Trigger convertReasonToTrigger(@NotNull SyncReason reason) {
    if (reason == SyncReason.PROJECT_LOADED) {
      return GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;
    } else if (reason == SyncReason.PROJECT_MODIFIED) {
      return GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
    } else {
      return GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;
    }
  }

  private void requestSync(@NotNull SyncReason reason, boolean requireSourceGeneration, @NotNull SettableFuture<SyncResult> syncResult) {
    GradleSyncStats.Trigger trigger = convertReasonToTrigger(reason);

    GradleSyncListener listener = new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        syncResult.set(SyncResult.SUCCESS);
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        syncResult.set(SyncResult.FAILURE);
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        syncResult.set(SyncResult.SKIPPED);
      }
    };

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setTrigger(trigger)
      .setGenerateSourcesOnSuccess(requireSourceGeneration).setRunInBackground(true);

    try {
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);
    } catch (Throwable t) {
      syncResult.setException(t);
    }
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
