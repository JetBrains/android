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
import com.android.tools.profiler.proto.Common.SessionData.SessionStarted
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profilers.cpu.CpuCaptureStageUtils
import com.android.tools.profilers.memory.MemoryProfiler
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.jvm.JvmStatic

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
  private fun copyToTemp(file: File, services: IdeProfilerServices, sessionType: SessionStarted.SessionType? = null): File {
    if (services.featureConfig.isSystemTraceInEditorEnabled && sessionType == SessionStarted.SessionType.CPU_CAPTURE) {
      val permanentFile = CpuCaptureStageUtils.getPermanentCaptureFile(services, file, file.name)
      if (permanentFile != null) {
        return permanentFile
      }
    }
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
    sessionType: SessionStarted.SessionType,
    makeEvent: (Long, Long) -> Common.Event
  ) {
    withFileImportedOnce(sessionsManager, file) { startTimestampsEpochMs, startTime, endTime ->
      val copiedFile = copyToTemp(file, sessionsManager.studioProfilers.ideServices, sessionType)
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
    sessionType: SessionStarted.SessionType,
  ) {
    withFileImportedOnce(sessionsManager, file) { startTimestampsEpochMs, startTime, endTime ->
      val copiedFile = copyToTemp(file, sessionsManager.studioProfilers.ideServices, sessionType)
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
   * This helper method creates an event used to mock the start event of a CPU or memory capture. This is the event that is inserted
   * into a fake session to wrap the imported data.
   */
  @JvmStatic
  fun makeStartedEvent(groupId: Long, timeStamp: Long, kind: Common.Event.Kind, prepare: Common.Event.Builder.() -> Unit): Common.Event =
    Common.Event.newBuilder()
      .setKind(kind)
      .setGroupId(groupId)
      .setTimestamp(timeStamp)
      .apply(prepare)
      .build()

  /**
   * This helper method creates an event used to mock the end event of a CPU or memory capture. This is the event that is inserted
   * into a fake session to wrap the imported data.
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
   * Helper function to ensure a file is only imported once. It checks if a session for the given file already exists based on its
   * timestamp. If so, it selects the existing session. Otherwise, it invokes the provided handler to perform the import.
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

  /**
   * This method takes in a file containing event groups, and generates a fake session + child artifact to recreate the imported data
   * in the Profiler.
   */
  @JvmStatic
  fun importEventBasedArtifact(
    sessionsManager: SessionsManager,
    file: File,
    sessionType: SessionStarted.SessionType,
    metaDataSessionType: Common.SessionMetaData.SessionType,
    makeArtifactEvents: ((Long, Long) -> List<Common.Event>)? = null
  ) {
    // Get the original timestamp range from the file. These will be our session times.
    val (sessionStartTimeNs, sessionEndTimeNs) = getTimestampRangeFromDbFile(file) ?: (0L to 0L)
    if (sessionStartTimeNs == 0L || sessionEndTimeNs == 0L) {
      getLogger().warn("Could not determine timestamp range from ${file.name}, aborting import.")
      return
    }

    // If a session with this start time already exists, just select it.
    if (sessionsManager.sessionIdToSessionItems.containsKey(sessionStartTimeNs)) {
      if (sessionsManager.studioProfilers.ideServices.featureConfig.isTaskBasedUxEnabled) {
        val session = sessionsManager.sessionIdToSessionItems[sessionStartTimeNs]!!
        sessionsManager.studioProfilers.pastRecordingsTabModel.recordingListModel.openRecording(session)
      }
      else {
        sessionsManager.setSessionById(sessionStartTimeNs)
      }
      return
    }

    // The time when the session is created. Will determine the order in Past Recordings panel.
    val startTimestampEpochMs = System.currentTimeMillis()
    var copiedFile: File? = null
    if (sessionsManager.studioProfilers.ideServices.featureConfig.isSystemTraceInEditorEnabled) {
      copiedFile = CpuCaptureStageUtils.getPermanentCaptureFile(
        sessionsManager.studioProfilers.ideServices,
        file,
        file.name)
    }
    if (copiedFile == null) {
      copiedFile = FileUtil.createTempFile("imported-${file.nameWithoutExtension}", ".${file.extension}", true)
    }

    try {
      // 1. Create the stream to get a streamId and server.
      val streamId = sessionsManager.createImportedSessionStream(startTimestampEpochMs)
      if (streamId == 0L) return
      val streamServer = sessionsManager.getEventStreamServer(streamId) ?: return

      // 2. Copy the database file to a temporary location that Studio will manage.
      FileUtil.copy(file, copiedFile)

      // 3. Connect to the copied database to read session metadata.
      getDbConnectionFromFile(copiedFile).use { connection ->
        val sessionData = getSessionInfoFromDb(connection)
        val exposureLevel = sessionData?.sessionStarted?.exposureLevel ?: Common.Process.ExposureLevel.UNKNOWN
        val jvmtiEnabled = sessionData?.sessionStarted?.jvmtiEnabled ?: false
        val events = makeArtifactEvents?.invoke(sessionStartTimeNs, sessionEndTimeNs)?.toTypedArray() ?: emptyArray()

        // 4. Create and populate the imported session with the data from the file.
        sessionsManager.populateImportedSession(
          sessionStartTimeNs,
          sessionEndTimeNs,
          startTimestampEpochMs,
          streamId,
          streamServer,
          file.name,
          sessionType,
          mapOf(sessionStartTimeNs.toString() to copiedFile.absolutePath),
          jvmtiEnabled,
          exposureLevel,
          *events
        )
        sessionsManager.studioProfilers.ideServices.featureTracker.trackCreateSession(metaDataSessionType,
                                                                                      SessionsManager.SessionCreationSource.MANUAL)
      }
    }
    catch (e: Exception) {
      getLogger().error("Failed to import artifact from database: ${file.path}", e)
    }
  }

  /**
   * Extracts session metadata (e.g., exposure level, JVMTI status) from the session start event in the database.
   */
  internal fun getSessionInfoFromDb(connection: Connection): Common.SessionData? {
    connection.prepareStatement("SELECT Data FROM UnifiedEventsTable WHERE Kind = ? AND IsEnded = 0 LIMIT 1").use { stmt ->
      stmt.setInt(1, Common.Event.Kind.SESSION_VALUE)
      stmt.executeQuery().use { rs ->
        if (rs.next()) {
          val data = rs.getBytes("Data")
          val event = Common.Event.parseFrom(data)
          if (event.hasSession()) {
            return event.session
          }
        }
      }
    }
    return null
  }

  /**
   * Reads metadata from an imported database file.
   * @return A map of metadata key-value pairs, or an empty map if metadata can't be read.
   */
  internal fun getDbMetadata(file: File): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    try {
      getDbConnectionFromFile(file).use { connection ->
        connection.createStatement().use { stmt ->
          stmt.executeQuery("SELECT key, value FROM _metadata").use { rs ->
            while (rs.next()) {
              val key = rs.getString("key")
              val value = rs.getString("value")
              if (key != null && value != null) {
                metadata[key] = value
              }
            }
          }
        }
      }
    }
    catch (e: Exception) {
      // This can happen if the file is not a valid DB or the table doesn't exist.
      getLogger().warn("Failed to read metadata from database: ${file.path}", e)
    }
    return metadata
  }

  /**
   * Queries a database file for the min and max event timestamps without opening it for writing.
   * @return A pair of (minTimestamp, maxTimestamp), or null if an error occurs or no events are found.
   */
  internal fun getTimestampRangeFromDbFile(file: File): Pair<Long, Long>? {
    return try {
      getDbConnectionFromFile(file).use { connection ->
        return getTimestampRangeFromDbConn(connection)
      }
    }
    catch (e: Exception) {
      getLogger().warn("Failed to read timestamp range from database: ${file.path}", e)
      null
    }
  }

  /**
   * Queries the database for the min and max event timestamps.
   * @return A pair of (minTimestamp, maxTimestamp), or null if no events are found.
   */
  private fun getTimestampRangeFromDbConn(connection: Connection): Pair<Long, Long>? {
    return connection.createStatement().use { stmt ->
      stmt.executeQuery("SELECT MIN(timestamp), MAX(timestamp) FROM UnifiedEventsTable").use { rs ->
        if (rs.next() && rs.getLong(1) != 0L) {
          Pair(rs.getLong(1), rs.getLong(2))
        }
        else {
          getLogger().warn("Imported event database contains no events")
          null
        }
      }
    }
  }

  /**
   * Creates a read-only JDBC connection to a SQLite database file.
   * @throws SQLException if a database access error occurs.
   */
  internal fun getDbConnectionFromFile(file: File): Connection {
    val properties = java.util.Properties()
    properties.setProperty("open_mode", "1") // SQLITE_OPEN_READONLY
    return DriverManager.getConnection("jdbc:sqlite:" + file.absolutePath, properties)
  }

  /**
   * Imports a task from an `.asdb` file. This function acts as a dispatcher, determining the task type from the file's metadata and
   * calling the appropriate import function.
   */
  @JvmStatic
  fun importAsdbTask(profilers: StudioProfilers, file: File) {
    val metadata = getDbMetadata(file)
    when (metadata["task_type"]) {
      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS.toString() -> MemoryProfiler.importAllocations(profilers, file)
      Common.ProfilerTaskType.LIVE_VIEW.toString() -> importLiveTask(profilers, file)
      Common.ProfilerTaskType.LEAKCANARY.toString() -> importLeakCanaryTask(profilers, file)
      else -> {
        getLogger().error("Imported .asdb file is not a recognized task type or is missing metadata: ${file.path}")
      }
    }
  }

  /**
   * Imports a Leak Canary task from an `.asdb` file.
   */
  private fun importLeakCanaryTask(profilers: StudioProfilers, file: File) {
    importEventBasedArtifact(profilers.sessionsManager,
                             file,
                             SessionStarted.SessionType.FULL,
                             Common.SessionMetaData.SessionType.FULL) { start, end ->
      listOf(
        makeStartedEvent(start, start, Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS) {
          setLeakCanaryAnalysisStatus(LeakCanary.LeakCanaryAnalysisStatus.newBuilder().setAnalysisEnded(
            LeakCanary.LeakCanaryAnalysisEnded.newBuilder().setStartTimestamp(start)
          ));
        },
        makeEndedEvent(start, end, Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS) {
          setLeakCanaryAnalysisStatus(LeakCanary.LeakCanaryAnalysisStatus.newBuilder().setAnalysisEnded(
            LeakCanary.LeakCanaryAnalysisEnded.newBuilder().setStartTimestamp(start).setEndTimestamp(end)
          ));
        },
      )
    }
  }

  /**
   * Imports a Live View task from an `.asdb` file.
   */
  private fun importLiveTask(profilers: StudioProfilers, file: File) {
    importEventBasedArtifact(profilers.sessionsManager,
                             file,
                             SessionStarted.SessionType.FULL,
                             Common.SessionMetaData.SessionType.FULL) { start, _ ->
      listOf(makeStartedEvent(start, start, Common.Event.Kind.LIVE_VIEW_STATUS) {})
    }
  }
}