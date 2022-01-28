/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.project.sync.AdditionalClassifierArtifactsActionOptions;
import com.android.tools.idea.gradle.project.sync.AllVariantsSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.AndroidExtraModelProvider;
import com.android.tools.idea.gradle.project.sync.GradleSyncStudioFlags;
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SyncActionOptions;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

public final class AndroidExtraModelProviderConfigurator {
  private AndroidExtraModelProviderConfigurator () {}
  @NotNull
  public static AndroidExtraModelProvider configureAndGetExtraModelProvider(@Nullable Project project,
                                                                            @NotNull ProjectResolverContext resolverCtx) {
    GradleExecutionSettings gradleExecutionSettings = resolverCtx.getSettings();
    ProjectResolutionMode projectResolutionMode = getRequestedSyncMode(gradleExecutionSettings);
    SyncActionOptions syncOptions;

    boolean parallelSync = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get();
    boolean parallelSyncPrefetchVariants = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS.get();
    GradleSyncStudioFlags studioFlags = new GradleSyncStudioFlags(
      parallelSync,
      parallelSyncPrefetchVariants,
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get(),
      AndroidGradleProjectResolver.shouldDisableForceUpgrades()
    );

    if (projectResolutionMode == ProjectResolutionMode.SyncProjectMode.INSTANCE) {
      // Here we set up the options for the sync and pass them to the AndroidExtraModelProvider which will decide which will use them
      // to decide which models to request from Gradle.
      AdditionalClassifierArtifactsActionOptions additionalClassifierArtifactsAction =
        new AdditionalClassifierArtifactsActionOptions(
          (project != null) ? LibraryFilePaths.getInstance(project).retrieveCachedLibs() : Collections.emptySet(),
          StudioFlags.SAMPLES_SUPPORT_ENABLED.get()
        );
      boolean isSingleVariantSync = project != null && !shouldSyncAllVariants(project);
      if (isSingleVariantSync) {
        SelectedVariantCollector variantCollector = new SelectedVariantCollector(project);
        SelectedVariants selectedVariants = variantCollector.collectSelectedVariants();
        String moduleWithVariantSwitched = project.getUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI);
        project.putUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null);
        syncOptions = new SingleVariantSyncActionOptions(
          studioFlags,
          selectedVariants,
          moduleWithVariantSwitched,
          additionalClassifierArtifactsAction
        );
      }
      else {
        syncOptions = new AllVariantsSyncActionOptions(studioFlags, additionalClassifierArtifactsAction);
      }
    }
    else if (projectResolutionMode instanceof ProjectResolutionMode.FetchNativeVariantsMode) {
      ProjectResolutionMode.FetchNativeVariantsMode fetchNativeVariantsMode =
        (ProjectResolutionMode.FetchNativeVariantsMode)projectResolutionMode;
      syncOptions = new NativeVariantsSyncActionOptions(studioFlags,
                                                        fetchNativeVariantsMode.getModuleVariants(),
                                                        fetchNativeVariantsMode.getRequestedAbis());
    }
    else {
      throw new IllegalStateException("Unknown FetchModelsMode class: " + projectResolutionMode.getClass().getName());
    }
    return new AndroidExtraModelProvider(syncOptions);
  }

  @NotNull
  private static ProjectResolutionMode getRequestedSyncMode(GradleExecutionSettings gradleExecutionSettings) {
    ProjectResolutionMode projectResolutionMode =
      gradleExecutionSettings != null ? gradleExecutionSettings.getUserData(AndroidGradleProjectResolverKeys.REQUESTED_PROJECT_RESOLUTION_MODE_KEY) : null;
    return projectResolutionMode != null ? projectResolutionMode : ProjectResolutionMode.SyncProjectMode.INSTANCE;
  }

  private static boolean shouldSyncAllVariants(@NotNull Project project) {
    Boolean shouldSyncAllVariants = project.getUserData(GradleSyncExecutor.ALL_VARIANTS_SYNC_KEY);
    return shouldSyncAllVariants != null && shouldSyncAllVariants;
  }
}