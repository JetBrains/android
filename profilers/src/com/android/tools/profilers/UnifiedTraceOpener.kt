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
import java.io.File

/** Helper class responsible for handling the opening of editor enabled tasks via the Unified Profiler. */
class UnifiedTraceOpener(private val profilers: StudioProfilers) {

  fun openUnifiedTrace(session: Common.Session, sessionItems: Map<Long, SessionItem>): Boolean {
    val currentTaskType = profilers.sessionsManager.currentTaskType

    // Try opening from a saved Artifact (Completed session)
    val sessionItem = sessionItems[session.sessionId] ?: return false
    val artifacts = sessionItem.getChildArtifacts()
    val isLegacyAllocations = sessionItem.isLegacyAllocations

    if (!ProfilerInEditorUtils.isEditorEnabled(profilers.ideServices.featureConfig, currentTaskType, isLegacyAllocations)) {
      return false
    }

    val isCpuTrace =
      currentTaskType == ProfilerTaskType.SYSTEM_TRACE ||
        currentTaskType == ProfilerTaskType.CALLSTACK_SAMPLE ||
        currentTaskType == ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING

    if (isCpuTrace) {
      val traceId = artifacts.firstNotNullOfOrNull { (it as? CpuCaptureSessionArtifact)?.artifactProto?.traceId } ?: return false
      return openTrace(session, traceId, ProfilerCaptureFileUtils.getTraceFile(traceId))
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
        openTrace(session, startTime, ProfilerCaptureFileUtils.getCaptureFile("capture_$startTime.$extension"))
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
}
