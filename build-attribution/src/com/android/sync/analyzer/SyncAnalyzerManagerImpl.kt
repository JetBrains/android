/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.sync.analyzer

import com.android.build.attribution.analytics.transformDownloadsAnalyzerData
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.output.DownloadInfoDataModel
import com.android.build.output.DownloadsInfoPresentableBuildEvent
import com.android.build.output.LongDownloadsNotifier
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.SyncAnalyzerManager
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleVersions
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.SyncViewManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.util.concurrent.ConcurrentHashMap

class SyncAnalyzerManagerImpl(
  val project: Project
) : SyncAnalyzerManager {

  override fun updateSyncStatsData(id: ExternalSystemTaskId?, syncStats: GradleSyncStats.Builder) {
    if (id == null) return
    project.getService(SyncAnalyzerDataManager::class.java).getDataForTaskIfExists(id)?.let { downloadsData ->
      syncStats.downloadsData = transformDownloadsAnalyzerData(downloadsData.downloadsStatsAccumulator.repositoryResults)
    }
  }

  override fun onSyncStarted(id: ExternalSystemTaskId?) {
    if (id == null) return
    val data = project.getService(SyncAnalyzerDataManager::class.java).getOrCreateDataForTask(id)
    project.setUpDownloadsInfoNodeOnBuildOutput(id, data)
  }

  override fun onSyncFinished(id: ExternalSystemTaskId?) {
    if (id == null) return
    project.getService(SyncAnalyzerDataManager::class.java).clearDataForTask(id)
  }

  private fun Project.setUpDownloadsInfoNodeOnBuildOutput(id: ExternalSystemTaskId, dataHolder: SyncAnalyzerDataManager.DataHolder) {
    if (!StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()) return
    if (dataHolder.downloadsInfoDataModel == null) return // It is not created if flag is disabled.
    val gradleVersion = GradleVersions.getInstance().getGradleVersion(this)
    val rootDownloadEvent = DownloadsInfoPresentableBuildEvent(id, dataHolder.buildDisposable, dataHolder.buildStartTimestampMs, gradleVersion, dataHolder.downloadsInfoDataModel)
    //dataHolder.downloadsInfoDataModel.longDownloadsNotifier =
    //  LongDownloadsNotifier(id, this, dataHolder.buildDisposable, dataHolder.buildStartTimestampMs)
    val viewManager = getService(SyncViewManager::class.java)
    viewManager.onEvent(id, rootDownloadEvent)
  }
}

@Service
class SyncAnalyzerDataManager {
  @VisibleForTesting
  val idToData = ConcurrentHashMap<ExternalSystemTaskId, DataHolder>()

  fun getOrCreateDataForTask(id: ExternalSystemTaskId): DataHolder = idToData.computeIfAbsent(id) { DataHolder(it) }

  fun getDataForTaskIfExists(id: ExternalSystemTaskId): DataHolder? = idToData[id]

  fun clearDataForTask(id: ExternalSystemTaskId) {
    idToData.remove(id)?.let { Disposer.dispose(it.buildDisposable) }
  }

  class DataHolder(val id: ExternalSystemTaskId) {
    val buildStartTimestampMs: Long = System.currentTimeMillis()
    val buildDisposable = Disposer.newCheckedDisposable("SyncAnalyzer disposable for $id")
    val downloadsStatsAccumulator = DownloadsAnalyzer.DownloadStatsAccumulator()

    // We don't want it to be created if feature is disabled. Once it is created it will be installed to build events listener and
    // involve running internal logic. To better isolate the feature using the flag just do not create it at all.
    val downloadsInfoDataModel = if (StudioFlags.BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()) {
      id.findProject()?.let { project ->
        val notifier = LongDownloadsNotifier(id, project, buildDisposable, buildStartTimestampMs)
        DownloadInfoDataModel(buildDisposable, notifier)
      }
    }
    else null
  }
}

class SyncAnalyzerOperationHelperExtension : GradleOperationHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings) {
    // Note: this method is called separately for buildSrc and project itself with the same `id` but with different operations.
    // This means we need to set up listener multiple times but with the same data accumulators.
    if (id.projectSystemId != GradleUtil.GRADLE_SYSTEM_ID) return
    if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
    val project = id.findProject() ?: return

    val syncData = project.getService(SyncAnalyzerDataManager::class.java).getDataForTaskIfExists(id)
    if (syncData != null) {
      val downloadEventsProcessor = DownloadsAnalyzer.DownloadEventsProcessor(syncData.downloadsStatsAccumulator, syncData.downloadsInfoDataModel)

      operation.addProgressListener(ProgressListener(downloadEventsProcessor::receiveEvent), OperationType.FILE_DOWNLOAD)
    }
  }

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) = Unit
}