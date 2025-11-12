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
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Helper class responsible for handling the opening of System Traces
 * via the Unified Profiler.
 */
class UnifiedTraceOpener(private val profilers: StudioProfilers) {

  fun openUnifiedTrace(
    session: Common.Session,
    sessionItems: Map<Long, SessionItem>
  ): Boolean {
    val services = profilers.ideServices
    val config = services.featureConfig

    // 1. Check Feature Flags and Task Type
    // We only intervene if the Unified Preview is enabled AND it is a System Trace task.
    if (!config.isSystemTraceInEditorEnabled ||
        profilers.sessionsManager.currentTaskType != ProfilerTaskType.SYSTEM_TRACE
    ) {
      return false
    }

    // 2. Optimization for imported sessions: open the original file directly (via EventStreamServer)
    // to avoid duplicating the file from the transport pipeline.
    val streamId = session.streamId
    val eventStreamServer = profilers.sessionsManager.getEventStreamServer(streamId)
    if (eventStreamServer != null) {
      val byteId = session.startTimestamp.toString()
      if (services.openFileFromEventStream(eventStreamServer, byteId)) {
        return true
      }
    }

    // 3. Try opening from a saved Artifact (Completed session)
    val sessionItem = sessionItems[session.sessionId] ?: return false

    // Find the first CpuCaptureSessionArtifact (Kotlin makes this cleaner than streams)
    val cpuArtifact = sessionItem.getChildArtifacts()
                        .filterIsInstance<CpuCaptureSessionArtifact>()
                        .firstOrNull() ?: return false

    return openArtifactFile(session, cpuArtifact)
  }

  private fun openArtifactFile(
    session: Common.Session,
    artifact: CpuCaptureSessionArtifact
  ): Boolean {
    val traceId = artifact.artifactProto.traceId

    // Ask the transport daemon for the file path
    val traceRequest = Transport.BytesRequest.newBuilder()
      .setStreamId(session.streamId)
      .setId(traceId.toString())
      .build()

    val traceResponse = profilers.client.transportClient.getFile(traceRequest)
    if (traceResponse.filePath.isEmpty()) {
      return false
    }

    // Resolve the actual file on disk
    var traceFile = File(traceResponse.filePath)
    val localCache = getLocalTraceCache(traceId)

    if (localCache.exists()) {
      traceFile = localCache
    }
    return profilers.ideServices.openTraceFile(traceFile)
  }

  private fun getLocalTraceCache(traceId: Long): File {
    var projectId = profilers.ideServices.projectHomeHash
    if (projectId.isEmpty()) {
      projectId = System.identityHashCode(profilers.ideServices).toHexString()
    }
    // Note: Ideally, "AndroidStudioProfiler" should be a constant in a shared utility class.
    val rootDir = File(FileUtil.getTempDirectory(), "AndroidStudioProfiler")
    val outputDir = File(rootDir, projectId)
    return File(outputDir, "capture_$traceId.trace")
  }

  // Tiny helper to replace Integer.toHexString
  private fun Int.toHexString(): String = Integer.toHexString(this)
}
