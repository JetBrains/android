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

import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.MessageBacklog
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.io.path.bufferedReader

private const val MONTH = "(?<month>\\d\\d)"
private const val DAY = "(?<day>\\d\\d)"
private const val HOUR = "(?<hour>\\d\\d)"
private const val MINUTE = "(?<minute>\\d\\d)"
private const val SECOND = "(?<second>\\d\\d)"
private const val MILLI = "(?<milli>\\d\\d\\d)"
private const val TIMESTAMP = "$MONTH-$DAY $HOUR:$MINUTE:$SECOND\\.$MILLI"
private const val PID = "(?<pid>\\d+)"
private const val TID = "(?<tid>\\d+)"
private const val LEVEL = "(?<level>[VDIWEA])"
private const val TAG_THREADTIME = "(?<tag>.+?(?=: ))"
private const val TAG_FIREBASE = "(?<tag>.+?(?=\\())"
private const val MESSAGE = "(?<message>.*)"

internal class LogcatFileParser(
  private val headerRegex: Regex,
  private val maxBufferSize: Int = AndroidLogcatSettings.getInstance().bufferSize,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

  fun parseLogcatFile(path: Path): List<LogcatMessage> {
    val messageBacklog = MessageBacklog(maxBufferSize)
    path.bufferedReader().use { reader ->
      var currentHeader: LogcatHeader? = null
      val currentMessage = StringBuilder()
      while (true) {
        val line = reader.readLine() ?: break
        if (line.startsWith(SYSTEM_LOG_PREFIX)) {
          messageBacklog.addAll(listOf(LogcatMessage(SYSTEM_HEADER, line)))
          currentHeader = null
          currentMessage.clear()
          continue
        }
        val result =
          headerRegex.find(line)
            ?: throw IllegalArgumentException("Error parsing $path. Invalid logcat line: $line")
        val header = result.toLogcatHeader(path.creationYear())
        if (header != currentHeader) {
          if (currentHeader != null) {
            messageBacklog.addAll(listOf(LogcatMessage(currentHeader, currentMessage.toString())))
          }
          currentHeader = header
          currentMessage.clear()
        }
        if (currentMessage.isNotEmpty()) {
          currentMessage.append('\n')
        }
        currentMessage.append(result.getGroup("message"))
      }
    }
    return messageBacklog.messages
  }

  companion object {
    val THREADTIME_REGEX = "^$TIMESTAMP +$PID +$TID $LEVEL $TAG_THREADTIME: $MESSAGE$".toRegex()
    val FIREBASE_REGEX = "^$TIMESTAMP: $LEVEL/$TAG_FIREBASE\\($PID\\): $MESSAGE$".toRegex()

    const val SYSTEM_LOG_PREFIX = "--------- beginning of "
  }

  private fun MatchResult.toLogcatHeader(year: Int): LogcatHeader {
    val month = getGroup("month").toInt()
    val day = getGroup("day").toInt()
    val hour = getGroup("hour").toInt()
    val minute = getGroup("minute").toInt()
    val second = getGroup("second").toInt()
    val nanos = TimeUnit.MILLISECONDS.toNanos(getGroup("milli").toLong()).toInt()
    val pid = getGroup("pid").toInt()
    val tid = runCatching { groups["tid"]?.value?.toInt() }.getOrNull() ?: 0
    val levelLetter = getGroup("level")
    val level =
      LogLevel.getByLetter(levelLetter)
        ?: throw IllegalArgumentException("Invalid log level: $levelLetter")
    val tag = getGroup("tag").trim()
    val processName = "pid-$pid"
    val timestamp =
      Instant.from(ZonedDateTime.of(year, month, day, hour, minute, second, nanos, zoneId))
    return LogcatHeader(level, pid, tid, processName, processName, tag, timestamp)
  }

  private fun Path.creationYear(): Int =
    Files.readAttributes(this, BasicFileAttributes::class.java)
      .creationTime()
      .toInstant()
      .atZone(zoneId)
      .year
}

private fun MatchResult.getGroup(group: String): String =
  groups[group]?.value ?: throw IllegalArgumentException("Group '$group' not found in $value")
