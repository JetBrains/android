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
package com.android.tools.profilers

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException

/**
 * This utility class extracts and exposes common functions needed to import a file and convert it to a session + artifact
 * in the Profiler.
 */
object ImportedSessionUtils {

  private fun getLogger() = Logger.getInstance(ImportedSessionUtils.javaClass)

  /**
   * Helper function to copy an imported file to a temporary location.
   * @return The copied file. If the copy fails, it logs a warning and returns the original file as a fallback.
   */
  private fun copyToTemp(file: File): File {
    return try {
      val tempFile = FileUtil.createTempFile("profiler-import-${file.nameWithoutExtension}", ".${file.extension}", true)
      FileUtil.copy(file, tempFile)
      tempFile
    }
    catch (e: IOException) {
      getLogger().warn("Failed to create a temporary copy of the imported file: ${file.path}. Using original file.", e)
      file
    }
  }

  /**
   * This method takes in a file imported by the user, and generates a fake session + child artifact to recreate the imported data
   * in the Profiler.
   */
  @JvmStatic
  fun importFileWithArtifactEvent(
    sessionsManager: SessionsManager,
    file: File,
    sessionType: Common.SessionData.SessionStarted.SessionType,
    makeEvent: (Long, Long) -> Common.Event
  ) {
    withFileImportedOnce(sessionsManager, file) { startTimestampsEpochMs, startTime, endTime ->
      val copiedFile = copyToTemp(file)
      sessionsManager.createImportedSession(
        file.name,
        sessionType,
        startTime, endTime,
        startTimestampsEpochMs,
        mapOf(startTime.toString() to copiedFile.absolutePath),
        makeEvent(startTime, endTime)
      )
    }
  }

  /**
   * This method takes in a file imported by the user, and generates only a fake session to recreate the imported data in the Profiler.
   * The child artifact is expected to be inserted later on if this method is used.
   */
  @JvmStatic
  fun importFile(
    sessionsManager: SessionsManager,
    file: File,
    sessionType: Common.SessionData.SessionStarted.SessionType,
  ) {
    withFileImportedOnce(sessionsManager, file) { startTimestampsEpochMs, startTime, endTime ->
      val copiedFile = copyToTemp(file)
      sessionsManager.createImportedSession(
        file.name,
        sessionType,
        startTime, endTime,
        startTimestampsEpochMs,
        mapOf(startTime.toString() to copiedFile.absolutePath),
      )
    }
  }

  /**
   * This helper method creates an event used to mock the end event of a CPU or memory capture. This is the event that is inserted
   * to a faked session to wrap the imported data.
   */
  @JvmStatic
  fun makeEndedEvent(groupId: Long, timeStamp: Long, kind: Common.Event.Kind, prepare: Common.Event.Builder.() -> Unit): Common.Event =
    Common.Event.newBuilder()
      .setKind(kind)
      .setGroupId(groupId)
      .setTimestamp(timeStamp)
      .setIsEnded(true)
      .apply(prepare)
      .build()

  /**
   * The following helper method reads the bytes of the imported file and sends the resulting bytes to a custom handler.
   */
  private fun withFileImportedOnce(sessionsManager: SessionsManager, file: File, handle: (Long, Long, Long) -> Unit) {
    // The time when the session is created. Will determine the order in sessions panel.
    val startTimestampEpochNs = System.currentTimeMillis()
    val timestampsNs = StudioProfilers.computeImportedFileStartEndTimestampsNs(file)
    val sessionStartTimeNs = timestampsNs.first
    val sessionEndTimeNs = timestampsNs.second
    val profilers = sessionsManager.studioProfilers
    when {
      // If the session of the file has already been imported, the session representing the imported file is selected. In Task-Based UX, in
      // addition to being selected, the session/recording is also opened in the task tab.
      sessionsManager.sessionIdToSessionItems.containsKey(sessionStartTimeNs) -> {
        if (profilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
          val session = sessionsManager.sessionIdToSessionItems[sessionStartTimeNs]!!
          profilers.pastRecordingsTabModel.recordingListModel.openRecording(session)
        }
        else {
          sessionsManager.setSessionById(sessionStartTimeNs)
        }
      }
      else -> handle(startTimestampEpochNs, sessionStartTimeNs, sessionEndTimeNs)
    }
  }
}