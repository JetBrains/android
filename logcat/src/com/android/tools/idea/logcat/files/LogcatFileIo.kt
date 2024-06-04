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
package com.android.tools.idea.logcat.files

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.files.LogcatFileData.Metadata
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.bufferedReader
import kotlin.io.path.reader
import kotlin.io.path.writer

private const val MAX_LOGCAT_ENTRY = 4000
private const val MONTH = "(?<month>\\d\\d)"
private const val DAY = "(?<day>\\d\\d)"
private const val HOUR = "(?<hour>\\d\\d)"
private const val MINUTE = "(?<minute>\\d\\d)"
private const val SECOND = "(?<second>\\d\\d)"
private const val MILLI = "(?<milli>\\d\\d\\d)"
private const val TIMESTAMP = "$MONTH-$DAY $HOUR:$MINUTE:$SECOND\\.$MILLI"
private const val UID = "\\w+"
private const val PID = "(?<pid>\\d+)"
private const val TID = "(?<tid>\\d+)"
private const val LEVEL = "(?<level>[VDIWEAF])"
private const val TAG_THREADTIME = "(?<tag>.+?(?=: ))"
private const val TAG_FIREBASE = "(?<tag>.+?(?=\\())"
private const val MESSAGE = "(?<message>.*)"
private val JSON_REGEX = "^\\{".toRegex()
private val THREADTIME_REGEX = "^$TIMESTAMP +$PID +$TID $LEVEL $TAG_THREADTIME: $MESSAGE$".toRegex()
private val BUGREPORT_REGEX =
  "^$TIMESTAMP +$UID +$PID +$TID $LEVEL $TAG_THREADTIME: $MESSAGE$".toRegex()
private val FIREBASE_REGEX = "^$TIMESTAMP: $LEVEL/$TAG_FIREBASE\\($PID\\): $MESSAGE$".toRegex()
private val BUGREPORT_FILE_REGEX =
  "^========================================================$".toRegex()

private val gson = GsonBuilder().setPrettyPrinting().create()

/** Contains functions to read and write a Logcat file */
internal class LogcatFileIo(private val zoneId: ZoneId = ZoneId.systemDefault()) {
  @Suppress("unused") // Used via `values()`
  private enum class LogcatFileType(val headerRegex: Regex) {
    JSON(JSON_REGEX) {
      override fun parse(path: Path, zoneId: ZoneId) = readJsonFile(path)
    },
    THREADTIME(THREADTIME_REGEX) {
      override fun parse(path: Path, zoneId: ZoneId) = parseLogcat(path, THREADTIME_REGEX, zoneId)
    },
    BUGREPORT(BUGREPORT_FILE_REGEX) {
      override fun parse(path: Path, zoneId: ZoneId) =
        parseLogcat(path, BUGREPORT_REGEX, zoneId, isBugreport = true)
    },
    FIREBASE(FIREBASE_REGEX) {
      override fun parse(path: Path, zoneId: ZoneId) = parseLogcat(path, FIREBASE_REGEX, zoneId)
    },
    UNKNOWN(".*".toRegex()) {
      override fun parse(path: Path, zoneId: ZoneId) =
        throw IllegalArgumentException("File '$path' is not a valid Logcat file")
    };

    abstract fun parse(path: Path, zoneId: ZoneId): LogcatFileData
  }

  fun writeLogcat(
    path: Path,
    logcatMessages: List<LogcatMessage>,
    device: Device,
    filter: String,
    projectApplicationIds: Set<String>,
  ) {
    val data = LogcatFileData(Metadata(device, filter, projectApplicationIds), logcatMessages)
    path.writer().use { gson.toJson(data, it) }
  }

  fun readLogcat(path: Path) = getLogcatFileType(path).parse(path, zoneId)

  private fun getLogcatFileType(path: Path): LogcatFileType {
    path.bufferedReader().use { reader ->
      val chars = CharArray(MAX_LOGCAT_ENTRY)
      reader.read(chars)
      val lines = String(chars).split("\n")
      val line = lines.first { !it.startsWith(LogcatFileParser.SYSTEM_LOG_PREFIX) }
      return LogcatFileType.entries.first { it.headerRegex.containsMatchIn(line) }
    }
  }
}

private fun readJsonFile(path: Path): LogcatFileData {
  return path.reader().use { gson.fromJson(it, LogcatFileData::class.java) }
}

private fun parseLogcat(
  path: Path,
  headerRegex: Regex,
  zoneId: ZoneId,
  isBugreport: Boolean = false,
) =
  LogcatFileData(
    null,
    LogcatFileParser(headerRegex, zoneId = zoneId).parseLogcatFile(path, isBugreport),
  )
