/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.ProfilerInEditorUtils
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskTypeMappingUtils
import com.android.tools.profilers.tasks.analytics.TaskDataOrigin
import com.android.tools.profilers.tasks.analytics.TaskFinishedState
import com.android.tools.profilers.tasks.analytics.TaskTracker
import com.google.wireless.android.sdk.stats.CpuImportTraceMetadata
import java.io.File

/** Helper class responsible for handling the opening of editor enabled tasks via the Unified Profiler. */
class UnifiedTraceOpener(private val profilers: StudioProfilers) {

  /**
   * Attempts to open the editor-supported trace associated with the given [session].
   *
   * @param session The [Common.Session] of the trace.
   * @param sessionItems Map of session IDs to [SessionItem]s.
   * @param taskTypeOverride Optional [ProfilerTaskType] to use instead of resolving from the session item.
   * @return true if the trace was opened in the editor, false otherwise.
   */
  @JvmOverloads
  fun openUnifiedTrace(
    session: Common.Session,
    sessionItems: Map<Long, SessionItem>,
    taskTypeOverride: ProfilerTaskType? = null,
  ): Boolean {
    val sessionItem = sessionItems[session.sessionId] ?: return false
    return openUnifiedTrace(sessionItem, taskTypeOverride)
  }

  /**
   * Attempts to open the editor-supported trace associated with the given [sessionItem].
   *
   * @param sessionItem The [SessionItem] containing the trace artifacts.
   * @param taskTypeOverride Optional [ProfilerTaskType] to use instead of resolving from the session item.
   * @return true if the trace was opened in the editor, false otherwise.
   */
  @JvmOverloads
  fun openUnifiedTrace(
    sessionItem: SessionItem,
    taskTypeOverride: ProfilerTaskType? = null,
  ): Boolean {
    val session = sessionItem.session
    val currentTaskType =
      taskTypeOverride
        ?: (sessionItem.getTaskType().takeIf { it != ProfilerTaskType.UNSPECIFIED }
          ?: TaskTypeMappingUtils.convertTaskType(sessionItem.sessionMetaData.taskType))
    val artifacts = sessionItem.getChildArtifacts()
    val isLegacyAllocations = sessionItem.isLegacyAllocations

    if (
      !ProfilerInEditorUtils.isEditorEnabled(profilers.ideServices.featureConfig, currentTaskType, isLegacyAllocations) ||
        ProfilerInEditorUtils.isLiveTaskInEditorEnabled(profilers.ideServices.featureConfig, currentTaskType, isLegacyAllocations)
    ) {
      return false
    }

    val isCpuTrace =
      currentTaskType == ProfilerTaskType.SYSTEM_TRACE ||
        currentTaskType == ProfilerTaskType.CALLSTACK_SAMPLE ||
        currentTaskType == ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING

    if (isCpuTrace) {
      val traceId = artifacts.firstNotNullOfOrNull { (it as? CpuCaptureSessionArtifact)?.artifactProto?.traceId } ?: return false
      val opened = openTrace(session, traceId, ProfilerCaptureFileUtils.getTraceFile(traceId))
      if (opened) {
        // Traces opened directly in the Editor bypass the legacy capture parsers where task metrics
        // are normally fired. We fire trackTaskEntered and trackTaskFinished here to ensure parity.
        val tracker = TaskTracker.createTaskTracker(profilers, session, currentTaskType)
        tracker.trackTaskEntered(profilerTabsCount = profilers.ideServices.profilerTabsCount)
        tracker.trackTaskFinished(TaskFinishedState.COMPLETED)

        if (tracker.taskMetadata.taskDataOrigin == TaskDataOrigin.IMPORTED) {
          val importMetadata =
            CpuImportTraceMetadata.newBuilder()
              .setTechnology(technologyForProfilerTaskType(currentTaskType))
              .setImportStatus(CpuImportTraceMetadata.ImportStatus.IMPORT_TRACE_SUCCESS)
              .build()
          profilers.ideServices.featureTracker.trackImportTrace(importMetadata)
        }
      }
      return opened
    }

    return artifacts
      .firstNotNullOfOrNull {
        when (it) {
          is HprofSessionArtifact -> Pair(it.artifactProto.startTime, "hprof")
          is LegacyAllocationsSessionArtifact -> Pair(it.artifactProto.startTime, "alloc")
          is HeapProfdSessionArtifact -> Pair(it.artifactProto.fromTimestamp, "heapprofd")
          else -> null
        }
      }
      ?.let { (startTime, extension) ->
        val opened = openTrace(session, startTime, ProfilerCaptureFileUtils.getCaptureFile("capture_$startTime.$extension"))
        if (opened) {
          // Traces opened directly in the Editor bypass the legacy capture parsers where task metrics
          // are normally fired. We fire trackTaskEntered and trackTaskFinished here to ensure parity.
          val tracker = TaskTracker.createTaskTracker(profilers, session, currentTaskType)
          tracker.trackTaskEntered(profilerTabsCount = profilers.ideServices.profilerTabsCount)
          tracker.trackTaskFinished(TaskFinishedState.COMPLETED)
        }
        opened
      } ?: false
  }

  private fun openTrace(session: Common.Session, traceId: Long, localCache: File): Boolean {
    // Ask the transport daemon for the file path
    val request = Transport.BytesRequest.newBuilder().setStreamId(session.streamId).setId(traceId.toString()).build()
    val response = profilers.client.transportClient.getFile(request)

    if (response.filePath.isEmpty()) {
      return false
    }

    // Resolve the actual file on disk
    val traceFile = localCache.takeIf { it.exists() } ?: File(response.filePath)

    return profilers.ideServices.openTraceFile(traceFile)
  }

  private fun technologyForProfilerTaskType(taskType: ProfilerTaskType): CpuImportTraceMetadata.Technology {
    return when (taskType) {
      ProfilerTaskType.SYSTEM_TRACE -> CpuImportTraceMetadata.Technology.PERFETTO_TECHNOLOGY
      ProfilerTaskType.CALLSTACK_SAMPLE -> CpuImportTraceMetadata.Technology.SIMPLEPERF_TECHNOLOGY
      ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING -> CpuImportTraceMetadata.Technology.ART_TECHNOLOGY
      else -> CpuImportTraceMetadata.Technology.UNKNOWN_TECHNOLOGY
    }
  }
}
