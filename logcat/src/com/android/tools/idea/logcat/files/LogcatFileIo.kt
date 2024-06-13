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
import com.android.tools.idea.logcat.files.LogcatFileIo.LogcatFileType.JSON
import com.android.tools.idea.logcat.files.LogcatFileIo.LogcatFileType.UNKNOWN
import com.android.tools.idea.logcat.message.LogcatMessage
import com.google.gson.GsonBuilder
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.bufferedReader
import kotlin.io.path.reader
import kotlin.io.path.writer

private const val MAX_LOGCAT_ENTRY = 4000

private val gson = GsonBuilder().setPrettyPrinting().create()

/** Contains functions to read and write a Logcat file */
internal class LogcatFileIo(private val zoneId: ZoneId = ZoneId.systemDefault()) {
  @Suppress("unused") // Used via `values()`
  private enum class LogcatFileType(val headerRegex: Regex) {
    JSON("^\\{".toRegex()),
    THREADTIME(LogcatFileParser.THREADTIME_REGEX),
    FIREBASE(LogcatFileParser.FIREBASE_REGEX),
    UNKNOWN(".*".toRegex()),
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

  fun readLogcat(path: Path): LogcatFileData {
    return when (val type = getLogcatFileType(path)) {
      JSON -> readJsonFile(path)
      UNKNOWN -> throw IllegalArgumentException("File '$path' is not a valid Logcat file")
      else ->
        LogcatFileData(
          null,
          LogcatFileParser(type.headerRegex, zoneId = zoneId).parseLogcatFile(path),
        )
    }
  }

  private fun readJsonFile(path: Path): LogcatFileData {
    return path.reader().use { gson.fromJson(it, LogcatFileData::class.java) }
  }

  private fun getLogcatFileType(path: Path): LogcatFileType {
    path.bufferedReader().use { reader ->
      val chars = CharArray(MAX_LOGCAT_ENTRY)
      reader.read(chars)
      val lines = String(chars).split("\n")
      val line = lines.first { !it.startsWith(LogcatFileParser.SYSTEM_LOG_PREFIX) }
      return LogcatFileType.values().first { it.headerRegex.containsMatchIn(line) }
    }
  }
}
