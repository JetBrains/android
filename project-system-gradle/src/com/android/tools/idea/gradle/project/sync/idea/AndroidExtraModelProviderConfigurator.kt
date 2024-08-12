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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.sync.AdditionalClassifierArtifactsActionOptions
import com.android.tools.idea.gradle.project.sync.AllVariantsSyncActionOptions
import com.android.tools.idea.gradle.project.sync.AndroidExtraModelProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncStudioFlags
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector
import com.android.tools.idea.gradle.project.sync.SelectedVariants
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SyncTestMode
import com.android.tools.idea.gradle.project.sync.getProjectSyncRequest
import com.android.tools.idea.gradle.project.sync.idea.ProjectResolutionMode.FetchAllVariantsMode
import com.android.tools.idea.gradle.project.sync.idea.ProjectResolutionMode.FetchNativeVariantsMode
import com.android.tools.idea.gradle.project.sync.idea.ProjectResolutionMode.SingleVariantSyncProjectMode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
private const val STUDIO_PROJECT_SYNC_DEBUG_MODE_KEY = "studio.project.sync.debug.mode"

fun studioProjectSyncDebugModeEnabled(): Boolean = java.lang.Boolean.getBoolean(STUDIO_PROJECT_SYNC_DEBUG_MODE_KEY)

fun ProjectResolverContext.configureAndGetExtraModelProvider(): AndroidExtraModelProvider? {
  ProgressManager.checkCanceled()
  val project = this.externalSystemTaskId.findProject() ?: let {
    thisLogger().error("Cannot find a project for $externalSystemTaskId", Throwable())
    return null // We can't be helpful if the current project is not available.
  }
  val projectResolutionMode = settings.getRequestedSyncMode()

  val parallelSync = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get() &&
                     GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC
  val parallelSyncPrefetchVariants = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS.get()

  val multiVariantArtifactSupport =
    GradleExperimentalSettings.getInstance().USE_MULTI_VARIANT_EXTRA_ARTIFACTS &&
    StudioFlags.GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT.get()

  val studioFlags = GradleSyncStudioFlags(
    studioLatestKnownAgpVersion = AgpVersions.latestKnown.toString(),
    studioFlagParallelSyncEnabled = parallelSync,
    studioFlagParallelSyncPrefetchVariantsEnabled = parallelSyncPrefetchVariants,
    studioFlagUseV2BuilderModels = StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get(),
    studioFlagDisableForcedUpgrades = AndroidGradleProjectResolver.shouldDisableForceUpgrades(),
    studioFlagSyncStatsOutputDirectory = StudioFlags.SYNC_STATS_OUTPUT_DIRECTORY.get(),
    studioHprofOutputDirectory = StudioFlags.GRADLE_HPROF_OUTPUT_DIRECTORY.get(),
    studioHeapAnalysisOutputDirectory = StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.get(),
    studioHeapAnalysisLightweightMode = StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.get(),
    studioFlagMultiVariantAdditionalArtifactSupport = multiVariantArtifactSupport,
    studioDebugMode =  studioProjectSyncDebugModeEnabled(),
    studioFlagSkipRuntimeClasspathForLibraries = StudioFlags.GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES.get()
                                                 && GradleExperimentalSettings.getInstance().DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES,
    studioFlagBuildRuntimeClasspathForLibraryUnitTests = StudioFlags.GRADLE_BUILD_RUNTIME_CLASSPATH_FOR_LIBRARY_UNIT_TESTS.get(),
    studioFlagBuildRuntimeClasspathForLibraryScreenshotTests = StudioFlags.GRADLE_BUILD_RUNTIME_CLASSPATH_FOR_LIBRARY_SCREENSHOT_TESTS.get(),
    studioFlagSupportFutureAgpVersions = StudioFlags.SUPPORT_FUTURE_AGP_VERSIONS.get(),
    studioFlagUseFlatDependencyGraphModel = StudioFlags.USE_FLAT_DEPENDENCY_GRAPH_MODEL.get(),
    studioFlagFetchKotlinModelsInParallel = StudioFlags.GRADLE_SYNC_FETCH_KOTLIN_MODELS_IN_PARALLEL.get()
  )

  fun getAdditionalArtifactsAction() = AdditionalClassifierArtifactsActionOptions(
    LibraryFilePaths.getInstance(project).retrieveCachedLibs(),
  )

  val syncOptions = when (projectResolutionMode) {
    SingleVariantSyncProjectMode -> {
      val request = project.getProjectSyncRequest(projectPath)
      // If the variants should be set to defaults, don't select any variants and the project with re-import with the defaults.
      val selectedVariants = if (request?.importDefaultVariants == true) {
        SelectedVariants(emptyMap())
      } else {
        SelectedVariantCollector(project).collectSelectedVariants()
      }
      SingleVariantSyncActionOptions(
        studioFlags,
        syncTestMode = request?.syncTestMode ?: SyncTestMode.PRODUCTION,
        selectedVariants,
        request?.requestedVariantChange,
        getAdditionalArtifactsAction()
      )
    }
    FetchAllVariantsMode -> AllVariantsSyncActionOptions(
      studioFlags,
      SyncTestMode.PRODUCTION, // No request in this mode.
      getAdditionalArtifactsAction()
    )
    is FetchNativeVariantsMode -> {
      NativeVariantsSyncActionOptions(
        studioFlags,
        SyncTestMode.PRODUCTION, // No request in this mode.
        projectResolutionMode.moduleVariants,
        projectResolutionMode.requestedAbis
      )
    }
  }
  return AndroidExtraModelProvider(syncOptions)
}

private fun GradleExecutionSettings?.getRequestedSyncMode(): ProjectResolutionMode {
  val projectResolutionMode = this?.getUserData(AndroidGradleProjectResolverKeys.REQUESTED_PROJECT_RESOLUTION_MODE_KEY)
  return projectResolutionMode ?: SingleVariantSyncProjectMode
}
