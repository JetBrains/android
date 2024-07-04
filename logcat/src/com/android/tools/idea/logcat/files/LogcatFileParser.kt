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
import com.google.common.collect.Iterators
import com.google.common.collect.PeekingIterator
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.bufferedReader
import kotlin.io.path.pathString

private const val BUGREPORT_LOGCAT_END = " was the duration of 'SYSTEM LOG' ------"
private const val BUGREPORT_LOGCAT_START = "------ SYSTEM LOG "

internal class LogcatFileParser(
  private val headerRegex: Regex,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

  fun parseLogcatFile(path: Path, isBugreport: Boolean = false): List<LogcatMessage> {
    return path.bufferedReader().use {
      parseLogcatFile(it, path.pathString, path.creationYear(), isBugreport)
    }
  }

  fun parseBugreportFile(path: Path): List<LogcatMessage> {
    val zip = ZipFile(path.pathString)
    val entries = zip.entries().asSequence()
    val entry =
      entries.find { it.isBugreport() }
        ?: throw IllegalArgumentException("Bugreport not found in $path")
    return zip.getInputStream(entry).bufferedReader().use {
      parseLogcatFile(it, path.pathString, path.creationYear(), true)
    }
  }

  private fun parseLogcatFile(
    reader: BufferedReader,
    filename: String,
    year: Int,
    isBugreport: Boolean,
  ): List<LogcatMessage> {
    var currentHeader: LogcatHeader? = null
    val currentMessage = StringBuilder()
    val iterator = Iterators.peekingIterator(reader.lineSequence().iterator().withIndex())
    if (isBugreport) {
      val found = iterator.consumeUntilLogcat()
      if (!found) {
        throw IllegalStateException("SYSTEM LOG not found")
      }
    }
    return buildList {
      run {
        iterator.forEach {
          val line = it.value
          if (isBugreport) {
            if (line.isEndOfLogcat()) {
              // Bugreport runs a second logcat command for logs generated during the bugreport.
              when (iterator.consumeUntilLogcat()) {
                true -> return@forEach
                false -> return@run
              }
            }
          }
          if (line.startsWith(SYSTEM_LOG_PREFIX)) {
            add(LogcatMessage(SYSTEM_HEADER, line))
            currentHeader = null
            currentMessage.clear()
            return@forEach
          }

          val result =
            headerRegex.find(line)
              ?: throw IllegalArgumentException(
                "Error parsing [$filename:${it.index + 1}]. Invalid logcat line: $line"
              )
          val header = result.toLogcatHeader(year)
          if (header != currentHeader) {
            currentHeader?.let { logcatHeader ->
              add(LogcatMessage(logcatHeader, currentMessage.toString()))
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
      currentHeader?.let {
        if (currentMessage.isNotEmpty()) {
          add(LogcatMessage(it, currentMessage.toString()))
        }
      }
    }
  }

  companion object {
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

private fun String.isEndOfLogcat() = endsWith(BUGREPORT_LOGCAT_END)

private fun PeekingIterator<IndexedValue<String>>.consumeUntilLogcat(): Boolean {
  while (hasNext() && !peek().value.startsWith(BUGREPORT_LOGCAT_START)) {
    next()
  }
  val found = hasNext()
  if (found) {
    next()
  }
  return found
}

private fun ZipEntry.isBugreport() = name.startsWith("bugreport-") && name.endsWith(".txt")
