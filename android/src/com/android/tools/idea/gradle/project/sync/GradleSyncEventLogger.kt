/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.gradle.model.IdeLibrary
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.stats.withProjectId
import com.google.common.collect.Ordering
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

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

  fun generateSyncEvent(project: Project, kind: AndroidStudioEvent.EventKind): AndroidStudioEvent.Builder {
    fun generateKotlinSupport(): KotlinSupport.Builder {
      var kotlinVersion: GradleVersion? = null
      var ktxVersion: GradleVersion? = null

      val ordering = Ordering.natural<GradleVersion>().nullsFirst<GradleVersion>()

      ModuleManager.getInstance(project).modules.mapNotNull { module -> AndroidModuleModel.get(module) }.forEach { model ->
        val dependencies = model.selectedMainCompileLevel2Dependencies

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
    syncStats.trigger = trigger
    syncStats.syncType = syncType
    syncStats.usesBuildGradle = buildFileTypes.contains(SdkConstants.DOT_GRADLE)
    syncStats.usesBuildGradleKts = buildFileTypes.contains(SdkConstants.DOT_KTS)

    val lastKnownVersion = GradleUtil.getLastKnownAndroidGradlePluginVersion(project)
    if (lastKnownVersion != null) syncStats.lastKnownAndroidGradlePluginVersion = lastKnownVersion
    val lastSuccessfulVersion = GradleUtil.getLastSuccessfulAndroidGradlePluginVersion(project)
    if (lastSuccessfulVersion != null) syncStats.androidGradlePluginVersion = lastSuccessfulVersion

    // Set up the Android studio event
    event.category = AndroidStudioEvent.EventCategory.GRADLE_SYNC
    event.kind = kind
    event.gradleSyncStats = syncStats.build()

    if (kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
      event.gradleVersion = GradleVersions.getInstance().getGradleVersion(project)?.toString() ?: ""
      event.setKotlinSupport(generateKotlinSupport())
    }
    return event.withProjectId(project)
  }
}

private fun Collection<IdeLibrary>.findVersion(artifact: String): GradleVersion? {
  val library = firstOrNull { library -> library.artifactAddress.startsWith(artifact) } ?: return null
  return GradleCoordinate.parseCoordinateString(library.artifactAddress)?.version
}
