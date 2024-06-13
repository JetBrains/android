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
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleVersions
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.SyncViewManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.service.project.GradleOperationHelperExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.util.concurrent.ConcurrentHashMap

class SyncAnalyzerManagerImpl(
  val project: Project
) : SyncAnalyzerManager {

  override fun updateSyncStatsData(id: ExternalSystemTaskId?, syncStats: GradleSyncStats.Builder) {
    if (id == null) return
    service<SyncAnalyzerDataManager>().getDataForTaskIfExists(id)?.let { downloadsData ->
      syncStats.downloadsData = transformDownloadsAnalyzerData(downloadsData.downloadsStatsAccumulator.repositoryResults)
    }
  }

  override fun onSyncStarted(id: ExternalSystemTaskId?) {
    if (id == null) return
    if (StudioFlags.isBuildOutputShowsDownloadInfo()) {
      val data = service<SyncAnalyzerDataManager>().getOrCreateDataForTask(id, project)
      project.setUpDownloadsInfoNodeOnBuildOutput(id, data)
    }
  }

  override fun onSyncFinished(id: ExternalSystemTaskId?) {
    if (id == null) return
    service<SyncAnalyzerDataManager>().clearDataForTask(id)
  }

  private fun Project.setUpDownloadsInfoNodeOnBuildOutput(id: ExternalSystemTaskId, dataHolder: SyncAnalyzerDataManager.DataHolder) {
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

  fun getOrCreateDataForTask(id: ExternalSystemTaskId, project: Project): DataHolder = idToData.computeIfAbsent(id) {
    Disposer.register(project) { clearDataForTask(id) }
    DataHolder(it, project)
  }

  fun getDataForTaskIfExists(id: ExternalSystemTaskId): DataHolder? = idToData[id]

  fun clearDataForTask(id: ExternalSystemTaskId) {
    idToData.remove(id)?.let { Disposer.dispose(it.buildDisposable) }
  }

  class DataHolder(val id: ExternalSystemTaskId, project: Project) {
    val buildStartTimestampMs: Long = System.currentTimeMillis()
    val buildDisposable = Disposer.newCheckedDisposable("SyncAnalyzer disposable for $id")
    val downloadsStatsAccumulator = DownloadsAnalyzer.DownloadStatsAccumulator()
    val downloadsInfoDataModel = DownloadInfoDataModel(buildDisposable, LongDownloadsNotifier(id, project, buildDisposable, buildStartTimestampMs))
  }
}

class SyncAnalyzerOperationHelperExtension : GradleOperationHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings,
                                   buildEnvironment: BuildEnvironment?) {
    // Note: this method is called separately for buildSrc and project itself with the same `id` but with different operations.
    // This means we need to set up listener multiple times but with the same data accumulators.
    if (id.projectSystemId != GradleProjectSystemUtil.GRADLE_SYSTEM_ID) return
    if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
    val project = id.findProject() ?: return

    val syncData = service<SyncAnalyzerDataManager>().getDataForTaskIfExists(id)
    if (syncData != null) {
      val downloadEventsProcessor = DownloadsAnalyzer.DownloadEventsProcessor(syncData.downloadsStatsAccumulator, syncData.downloadsInfoDataModel)

      operation.addProgressListener(ProgressListener(downloadEventsProcessor::receiveEvent), OperationType.FILE_DOWNLOAD)
    }
  }

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) = Unit
}