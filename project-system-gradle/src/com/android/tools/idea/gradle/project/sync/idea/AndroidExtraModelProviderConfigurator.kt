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
import com.android.tools.idea.gradle.project.sync.AdditionalClassifierArtifactsActionOptions
import com.android.tools.idea.gradle.project.sync.AllVariantsSyncActionOptions
import com.android.tools.idea.gradle.project.sync.AndroidExtraModelProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncStudioFlags
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions
import com.android.tools.idea.gradle.project.sync.idea.ProjectResolutionMode.FetchNativeVariantsMode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

fun ProjectResolverContext.configureAndGetExtraModelProvider(): AndroidExtraModelProvider? {
  val project = this.externalSystemTaskId.findProject() ?: let {
    thisLogger().error("Cannot find a project for $externalSystemTaskId", Throwable())
    return null // We can't be helpful if the current project is not available.
  }
  val projectResolutionMode = settings.getRequestedSyncMode()

  val parallelSync = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get()
  val parallelSyncPrefetchVariants = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS.get()

  val studioFlags = GradleSyncStudioFlags(
    parallelSync,
    parallelSyncPrefetchVariants,
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get(),
    AndroidGradleProjectResolver.shouldDisableForceUpgrades()
  )

  fun getAdditionalArtifactsAction() = AdditionalClassifierArtifactsActionOptions(
    LibraryFilePaths.getInstance(project).retrieveCachedLibs(),
    StudioFlags.SAMPLES_SUPPORT_ENABLED.get()
  )

  val syncOptions = when (projectResolutionMode) {
    ProjectResolutionMode.SyncProjectMode -> {
      if (project?.shouldSyncAllVariants() == true) {
        AllVariantsSyncActionOptions(studioFlags, getAdditionalArtifactsAction())
      } else {
        val selectedVariants = SelectedVariantCollector(project!!).collectSelectedVariants()
        val moduleWithVariantSwitched =
          project.getUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI)
        project.putUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null)
        SingleVariantSyncActionOptions(
          studioFlags,
          selectedVariants,
          moduleWithVariantSwitched,
          getAdditionalArtifactsAction()
        )
      }
    }
    is FetchNativeVariantsMode -> {
      NativeVariantsSyncActionOptions(
        studioFlags,
        projectResolutionMode.moduleVariants,
        projectResolutionMode.requestedAbis
      )
    }
  }
  return AndroidExtraModelProvider(syncOptions)
}

private fun GradleExecutionSettings?.getRequestedSyncMode(): ProjectResolutionMode {
  val projectResolutionMode = this?.getUserData(AndroidGradleProjectResolverKeys.REQUESTED_PROJECT_RESOLUTION_MODE_KEY)
  return projectResolutionMode ?: ProjectResolutionMode.SyncProjectMode
}

private fun Project.shouldSyncAllVariants(): Boolean {
  val shouldSyncAllVariants = getUserData(GradleSyncExecutor.ALL_VARIANTS_SYNC_KEY)
  return shouldSyncAllVariants != null && shouldSyncAllVariants
}