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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeArtifactDependency
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.stats.withProjectId
import com.google.common.collect.Ordering
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.SystemIndependent

class GradleSyncEventLogger(val now: () -> Long = { System.currentTimeMillis() }) {
  // Negative numbers mean that the events have not finished
  private var syncStartedTimeStamp = -1L
  private var syncSetupStartedTimeStamp = -1L
  private var syncEndedTimeStamp = -1L

  private var syncType: GradleSyncStats.GradleSyncType? = null
  private var trigger: GradleSyncStats.Trigger? = null

  fun syncStarted(syncType: GradleSyncStats.GradleSyncType, trigger: GradleSyncStats.Trigger) {
    syncStartedTimeStamp = now()
    syncSetupStartedTimeStamp = -1L
    syncEndedTimeStamp = -1L

    this.syncType = syncType
    this.trigger = trigger
  }

  fun setupStarted(): Long {
    syncSetupStartedTimeStamp = now()
    return syncSetupStartedTimeStamp - syncStartedTimeStamp
  }

  fun syncEnded(): Long {
    syncEndedTimeStamp = now()
    return syncEndedTimeStamp - syncStartedTimeStamp
  }

  fun generateSyncEvent(
    project: Project,
    rootProjectPath: @SystemIndependent String?,
    kind: AndroidStudioEvent.EventKind
  ): AndroidStudioEvent.Builder {
    fun generateKotlinSupport(): KotlinSupport.Builder {
      var kotlinVersion: GradleVersion? = null
      var ktxVersion: GradleVersion? = null

      val ordering = Ordering.natural<GradleVersion>().nullsFirst<GradleVersion>()

      ModuleManager.getInstance(project).modules.mapNotNull { module -> GradleAndroidModel.get(module) }.forEach { model ->
        val dependencies = model.selectedMainCompileDependencies

        kotlinVersion = ordering.max(kotlinVersion, dependencies.javaLibraries.findVersion("org.jetbrains.kotlin:kotlin-stdlib"))
        ktxVersion = ordering.max(ktxVersion, dependencies.androidLibraries.findVersion("androidx.core:core-ktx"))
      }

      val kotlinSupport = KotlinSupport.newBuilder()
      if (kotlinVersion != null) kotlinSupport.kotlinSupportVersion = kotlinVersion.toString()
      if (ktxVersion != null) kotlinSupport.androidKtxVersion = ktxVersion.toString()
      return kotlinSupport
    }

    val event = AndroidStudioEvent.newBuilder()
    val syncStats = GradleSyncStats.newBuilder()
    val buildFileTypes = GradleUtil.projectBuildFilesTypes(project)

    // Setup the sync stats
    syncStats.totalTimeMs = when {
      syncEndedTimeStamp >= 0 -> syncEndedTimeStamp - syncStartedTimeStamp // Sync was successful
      syncSetupStartedTimeStamp >= 0 -> syncSetupStartedTimeStamp - syncStartedTimeStamp // Only fetching model has finished
      else -> 0
    }
    syncStats.ideTimeMs = when {
      syncEndedTimeStamp < 0 -> -1  // Sync is in progress or something went wrong during the last sync
      syncSetupStartedTimeStamp < 0 -> -1 // Sync was done from cache (no gradle nor IDE part was done)
      else -> syncEndedTimeStamp - syncSetupStartedTimeStamp
    }
    syncStats.gradleTimeMs = if (syncSetupStartedTimeStamp >= 0) syncSetupStartedTimeStamp - syncStartedTimeStamp else -1
    syncStats.trigger = trigger ?: GradleSyncStats.Trigger.TRIGGER_UNKNOWN
    syncStats.syncType = syncType ?: GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_UNKNOWN
    syncStats.usesBuildGradle = buildFileTypes.contains(SdkConstants.DOT_GRADLE)
    syncStats.usesBuildGradleKts = buildFileTypes.contains(SdkConstants.DOT_KTS)
    if (rootProjectPath != null) {
      syncStats.updateUserRequestedParallelSyncMode(project, rootProjectPath)
    }

    runReadAction {
      val lastKnownVersion = GradleUtil.getLastKnownAndroidGradlePluginVersion(project)
      if (lastKnownVersion != null) syncStats.lastKnownAndroidGradlePluginVersion = lastKnownVersion
      val lastSuccessfulVersion = GradleUtil.getLastSuccessfulAndroidGradlePluginVersion(project)
      if (lastSuccessfulVersion != null) syncStats.androidGradlePluginVersion = lastSuccessfulVersion

      // Set up the Android studio event
      event.category = AndroidStudioEvent.EventCategory.GRADLE_SYNC
      event.kind = kind

      if (kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
        event.gradleVersion = GradleVersions.getInstance().getGradleVersion(project)?.toString() ?: ""
        event.setKotlinSupport(generateKotlinSupport())
      }
      event.withProjectId(project)

      event.gradleSyncStats = syncStats.build()
    }
    return event
  }
}

private fun Collection<IdeArtifactDependency<*>>.findVersion(artifact: String): GradleVersion? {
  val library = firstOrNull { library -> library.target.artifactAddress.startsWith(artifact) } ?: return null
  return GradleCoordinate.parseCoordinateString(library.target.artifactAddress)?.version
}

private fun GradleSyncStats.Builder.updateUserRequestedParallelSyncMode(project: Project, rootProjectPath: @SystemIndependent String) {
  if (!StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get()) {
    clearUserRequestedSyncType()
    return
  }

  userRequestedSyncType = when (GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC) {
    true -> GradleSyncStats.UserRequestedExecution.USER_REQUESTED_PARALLEL
    false -> GradleSyncStats.UserRequestedExecution.USER_REQUESTED_SEQUENTIAL
  }

  val projectData = ExternalSystemApiUtil.findProjectNode(project, GradleUtil.GRADLE_SYSTEM_ID, rootProjectPath)
  val executionReport = projectData?.let { ExternalSystemApiUtil.find(projectData, AndroidProjectKeys.SYNC_EXECUTION_REPORT) }?.data
  if (executionReport != null) {
    studioRequestedSyncType = when (executionReport.parallelFetchForV2ModelsEnabled) {
      true -> GradleSyncStats.StudioRequestedExecution.STUDIO_REQUESTD_PARALLEL
      false -> GradleSyncStats.StudioRequestedExecution.STUDIO_REQUESTD_SEQUENTIAL
    }
  }
}