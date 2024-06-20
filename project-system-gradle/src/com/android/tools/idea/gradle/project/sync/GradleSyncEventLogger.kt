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
import com.android.ide.common.gradle.Version
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleVersions
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

  private val gradleSyncPhasesProfile: ArrayList<GradleSyncStats.GradleSyncPhaseData> = arrayListOf()
  private val phasesStack = ArrayDeque<GradleSyncPhaseStartEvent>()

  private var syncType: GradleSyncStats.GradleSyncType? = null
  private var trigger: GradleSyncStats.Trigger? = null

  @Synchronized
  fun syncStarted(syncType: GradleSyncStats.GradleSyncType, trigger: GradleSyncStats.Trigger) {
    syncStartedTimeStamp = now()
    syncSetupStartedTimeStamp = -1L
    syncEndedTimeStamp = -1L
    phasesStack.clear()
    gradleSyncPhasesProfile.clear()

    this.syncType = syncType
    this.trigger = trigger

    syncPhaseStarted(GradleSyncStats.GradleSyncPhaseData.SyncPhase.SYNC_TOTAL)
  }

  @Synchronized
  fun setupStarted(): Long {
    syncSetupStartedTimeStamp = now()
    syncPhaseStarted(GradleSyncStats.GradleSyncPhaseData.SyncPhase.PROJECT_SETUP)
    return syncSetupStartedTimeStamp - syncStartedTimeStamp
  }

  @Synchronized
  fun syncEnded(success: Boolean): Long {
    syncEndedTimeStamp = now()
    if (success) {
      stopAllPhases(GradleSyncStats.GradleSyncPhaseData.PhaseResult.SUCCESS)
    }
    else {
      stopAllPhases(GradleSyncStats.GradleSyncPhaseData.PhaseResult.FAILURE)
    }
    return syncEndedTimeStamp - syncStartedTimeStamp
  }

  @Synchronized
  fun syncCancelled() {
    stopAllPhases(GradleSyncStats.GradleSyncPhaseData.PhaseResult.CANCELLED)
  }

  @Synchronized
  fun syncPhaseStarted(phase: GradleSyncStats.GradleSyncPhaseData.SyncPhase) {
    phasesStack.addLast(GradleSyncPhaseStartEvent(phase, now()))
  }

  @Synchronized
  fun gradlePhaseFinished(
    phase: GradleSyncStats.GradleSyncPhaseData.SyncPhase,
    startTimestamp: Long,
    endTimestamp: Long,
    status: GradleSyncStats.GradleSyncPhaseData.PhaseResult
  ) {
    // It should always be this value on top of the stack normally, but check just in case to not blindly remove a different value.
    if (phasesStack.lastOrNull()?.phase == phase) {
      phasesStack.removeLastOrNull()
    }
    val finishedPhaseData = GradleSyncStats.GradleSyncPhaseData.newBuilder()
      .addAllPhaseStack(phasesStack.map { it.phase } + phase)
      .setPhaseStartTimestampMs(startTimestamp)
      .setPhaseEndTimestampMs(endTimestamp)
      .setPhaseResult(status)
    gradleSyncPhasesProfile.add(finishedPhaseData.build())
  }

  private fun stopAllPhases(status: GradleSyncStats.GradleSyncPhaseData.PhaseResult) {
    while (phasesStack.isNotEmpty()) {
      val finishedPhaseData = GradleSyncStats.GradleSyncPhaseData.newBuilder()
        .addAllPhaseStack(phasesStack.map { it.phase })
        .setPhaseStartTimestampMs(phasesStack.last().startTimestamp)
        .setPhaseEndTimestampMs(syncEndedTimeStamp)
        .setPhaseResult(status)
      gradleSyncPhasesProfile.add(finishedPhaseData.build())
      phasesStack.removeLast()
    }
  }

  @Synchronized
  fun generateSyncEvent(
    project: Project,
    rootProjectPath: @SystemIndependent String?,
    kind: AndroidStudioEvent.EventKind,
    updateAdditionalData: GradleSyncStats.Builder.() -> Unit = {}
  ): AndroidStudioEvent.Builder {
    fun generateKotlinSupport(): KotlinSupport.Builder {
      var kotlinVersion: Version? = null
      var ktxVersion: Version? = null

      val ordering = Ordering.natural<Version>().nullsFirst<Version>()

      ModuleManager.getInstance(project).modules.mapNotNull { module -> GradleAndroidModel.get(module) }.forEach { model ->
        val dependencies = model.mainArtifact.compileClasspath

        kotlinVersion = ordering.max(kotlinVersion, dependencies.libraries.findVersion("org.jetbrains.kotlin", "kotlin-stdlib"))
        ktxVersion = ordering.max(ktxVersion, dependencies.libraries.findVersion("androidx.core", "core-ktx"))
      }

      val kotlinSupport = KotlinSupport.newBuilder()
      if (kotlinVersion != null) kotlinSupport.kotlinSupportVersion = kotlinVersion.toString()
      if (ktxVersion != null) kotlinSupport.androidKtxVersion = ktxVersion.toString()
      return kotlinSupport
    }

    val event = AndroidStudioEvent.newBuilder()
    val syncStats = GradleSyncStats.newBuilder()
    val buildFileTypes = GradleProjectSystemUtil.projectBuildFilesTypes(project)

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
    syncStats.versionCatalogDetectorState = GradleVersionCatalogDetector.getInstance(project).versionCatalogDetectorResultIfAvailable.state
    if (rootProjectPath != null) {
      syncStats.updateUserRequestedParallelSyncMode(project, rootProjectPath)
    }
    syncStats.updateAdditionalData()
    if (kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED
        || kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_CANCELLED
        || kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE
        || kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS) {
      syncStats.addAllGradleSyncPhasesData(gradleSyncPhasesProfile)
    }

    val gradleVersion = when(kind) {
      AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED -> GradleVersions.getInstance().getGradleVersion(project)?.version ?: ""
      else -> null
    }
    runReadAction {
      val lastKnownVersion =
        GradleProjectSystemUtil.getLastKnownAndroidGradlePluginVersion(project)
      if (lastKnownVersion != null) syncStats.lastKnownAndroidGradlePluginVersion = lastKnownVersion
      val lastSuccessfulVersion =
        GradleProjectSystemUtil.getLastSuccessfulAndroidGradlePluginVersion(project)
      if (lastSuccessfulVersion != null) syncStats.androidGradlePluginVersion = lastSuccessfulVersion

      // Set up the Android studio event
      event.category = AndroidStudioEvent.EventCategory.GRADLE_SYNC
      event.kind = kind

      gradleVersion?.let { event.gradleVersion = it }
      if (kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
        event.setKotlinSupport(generateKotlinSupport())
      }
      event.withProjectId(project)

      event.gradleSyncStats = syncStats.build()
    }
    return event
  }
}

data class GradleSyncPhaseStartEvent(
  val phase: GradleSyncStats.GradleSyncPhaseData.SyncPhase,
  val startTimestamp: Long
)

private fun Collection<IdeLibrary>.findVersion(group: String, name: String): Version? =
  filterIsInstance<IdeArtifactLibrary>()
    .firstOrNull { library -> library.component?.let { it.group == group && it.name == name } ?: false }
    ?.component?.version

private fun GradleSyncStats.Builder.updateUserRequestedParallelSyncMode(project: Project, rootProjectPath: @SystemIndependent String) {
  if (!StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get()) {
    clearUserRequestedSyncType()
    return
  }

  userRequestedSyncType = when (GradleExperimentalSettings.getInstance().ENABLE_PARALLEL_SYNC) {
    true -> GradleSyncStats.UserRequestedExecution.USER_REQUESTED_PARALLEL
    false -> GradleSyncStats.UserRequestedExecution.USER_REQUESTED_SEQUENTIAL
  }

  val projectData = ExternalSystemApiUtil.findProjectNode(project,
                                                          GradleProjectSystemUtil.GRADLE_SYSTEM_ID, rootProjectPath)
  val executionReport = projectData?.let { ExternalSystemApiUtil.find(projectData, AndroidProjectKeys.SYNC_EXECUTION_REPORT) }?.data
  if (executionReport != null) {
    studioRequestedSyncType = when (executionReport.parallelFetchForV2ModelsEnabled) {
      true -> GradleSyncStats.StudioRequestedExecution.STUDIO_REQUESTD_PARALLEL
      false -> GradleSyncStats.StudioRequestedExecution.STUDIO_REQUESTD_SEQUENTIAL
    }
  }
}