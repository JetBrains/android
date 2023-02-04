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
import com.android.tools.idea.gradle.project.sync.SyncAnalyzerManager
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
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
      syncStats.downloadsData = transformDownloadsAnalyzerData(downloadsData.repositoryResults)
    }
  }

  override fun onSyncFinished(id: ExternalSystemTaskId?) {
    if (id == null) return
    project.getService(SyncAnalyzerDataManager::class.java).clearDataForTask(id)
  }
}

@Service
class SyncAnalyzerDataManager {
  @VisibleForTesting
  val idToData = ConcurrentHashMap<ExternalSystemTaskId, DownloadsAnalyzer.DownloadStatsAccumulator>()

  fun getOrCreateDataForTask(id: ExternalSystemTaskId): DownloadsAnalyzer.DownloadStatsAccumulator = idToData.computeIfAbsent(id) {
    DownloadsAnalyzer.DownloadStatsAccumulator()
  }

  fun getDataForTaskIfExists(id: ExternalSystemTaskId): DownloadsAnalyzer.DownloadStatsAccumulator? = idToData[id]

  fun clearDataForTask(id: ExternalSystemTaskId) {
    idToData.remove(id)
  }
}

class SyncAnalyzerOperationHelperExtension : GradleOperationHelperExtension {

  override fun prepareForExecution(id: ExternalSystemTaskId,
                                   operation: LongRunningOperation,
                                   gradleExecutionSettings: GradleExecutionSettings) {
    // Note: this method is called separately for buildSrc and project itself with the same `id` but with different operations.
    if (id.projectSystemId != GradleUtil.GRADLE_SYSTEM_ID) return
    if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
    val project = id.findProject() ?: return
    val downloadsStatsAccumulator = project.getService(SyncAnalyzerDataManager::class.java).getOrCreateDataForTask(id)
    val progressListener = ProgressListener { event ->
      downloadsStatsAccumulator.receiveEvent(event)
    }
    operation.addProgressListener(progressListener, OperationType.FILE_DOWNLOAD)
  }

  override fun prepareForSync(operation: LongRunningOperation, resolverCtx: ProjectResolverContext) = Unit
}