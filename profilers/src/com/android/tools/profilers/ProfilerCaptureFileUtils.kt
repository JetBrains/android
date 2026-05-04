/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.cpu.CpuCaptureParserUtil
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.utils.ProfilerHashUtils
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File

object ProfilerCaptureFileUtils {
  /**
   * Renames a temporary capture file to an identifiable file within the system's temporary directory.
   *
   * This is used when a capture is created and needs to be saved with a specific name so it can be easily identified and opened by the
   * editor.
   *
   * @param captureFile the source temporary file
   * @param targetFileName the desired name for the file
   * @return the renamed [File] if successful, or null otherwise
   */
  @JvmStatic
  fun renameToTargetFile(captureFile: File, targetFileName: String): File? {
    // Use the system temp directory as the destination folder for the permanent file.
    val outputDir = File(FileUtil.getTempDirectory())
    val traceFile = File(outputDir, targetFileName)

    // Try to rename the capture file to the target file.
    if (captureFile.renameTo(traceFile)) {
      return traceFile
    }
    return null
  }

  @JvmStatic
  fun getTraceFile(traceId: Long): File {
    return File(FileUtil.getTempDirectory(), "capture_$traceId.trace")
  }

  @JvmStatic
  fun getCaptureFile(traceFileName: String): File {
    return File(FileUtil.getTempDirectory(), traceFileName)
  }

  @JvmStatic
  fun getCaptureAsFile(profilers: StudioProfilers, traceId: Long): File? {
    val traceRequest = Transport.BytesRequest.newBuilder().setStreamId(profilers.session.streamId).setId(traceId.toString()).build()
    val traceResponse = profilers.client.transportClient.getFile(traceRequest)
    if (traceResponse.filePath.isEmpty()) return null
    val captureFile = File(traceResponse.filePath)
    if (!captureFile.exists() || captureFile.length() == 0L) return null
    return captureFile
  }

  @JvmStatic
  fun getAndRenameCapture(profilers: StudioProfilers, session: Common.Session, traceId: Long): File? {
    val captureFile = getCaptureAsFile(profilers, traceId) ?: return null
    val finalFile = renameToTargetFile(captureFile, getTraceFile(traceId).name) ?: return null
    writeMetadataToFile(profilers, session, finalFile)
    return finalFile
  }

  /**
   * Writes a sidecar file containing serialized task metadata alongside the trace file. This metadata is used by standalone editors to
   * reconstruct telemetry dashboards accurately safely.
   */
  @JvmStatic
  fun writeMetadataToFile(profilers: StudioProfilers, session: Common.Session, file: File) {
    val sessionsManager = profilers.sessionsManager
    val metadata = sessionsManager.getSessionMetaData(session.sessionId) ?: return
    try {
      val hash = ProfilerHashUtils.hashTracePath(file)
      val tempDir = FileUtil.getTempDirectory()
      val metadataFile = File(tempDir, "${file.nameWithoutExtension}-$hash.metadata")
      if (!metadataFile.exists()) {
        val taskMetadata =
          ProfilerTaskMetadataProto.ProfilerTaskMetadata.newBuilder()
            .setSessionMetadata(metadata)
            .setTaskDataOrigin(resolveDataOrigin(sessionsManager, session))
            .setTaskAttachmentPoint(resolveAttachmentPoint(sessionsManager))
            .build()

        metadataFile.outputStream().use { output -> taskMetadata.writeTo(output) }
      }
    } catch (e: Exception) {
      Logger.getInstance(ProfilerCaptureFileUtils::class.java).warn("Failed to write metadata for ${file.absolutePath}", e)
    }
  }

  private fun resolveDataOrigin(
    sessionsManager: SessionsManager,
    session: Common.Session,
  ): ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin {
    return when {
      sessionsManager.isSessionAlive -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin.NEW
      SessionsManager.isSessionImported(session) -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin.IMPORTED
      session != Common.Session.getDefaultInstance() -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin.PAST_RECORDING
      else -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin.ORIGIN_UNSPECIFIED
    }
  }

  private fun resolveAttachmentPoint(sessionsManager: SessionsManager): ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint {
    return when {
      !sessionsManager.isSessionAlive -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint.ATTACHMENT_UNSPECIFIED
      sessionsManager.isCurrentTaskStartup -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint.NEW_PROCESS
      else -> ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint.EXISTING_PROCESS
    }
  }

  /** Returns the corresponding [ProfilerTaskType] for a given trace [File], based on its extension. */
  @JvmStatic
  fun getFileTaskType(file: File): ProfilerTaskType? {
    val lazyTraceType = lazy { CpuCaptureParserUtil.getFileTraceType(file, TraceType.UNSPECIFIED) }
    return ProfilerFormat.find(file.extension, lazyTraceType)?.taskType
  }
}
