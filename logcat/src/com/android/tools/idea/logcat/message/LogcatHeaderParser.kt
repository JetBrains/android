/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.message

/** TODO */
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.EPOCH_FORMAT
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit.MILLISECONDS

private val EPOCH = Regex("(?<epochSec>\\d+)\\.(?<epochMilli>\\d\\d\\d)")

private val DATE = Regex("(?<month>\\d\\d)-(?<day>\\d\\d)")

private val TIME = Regex("(?<hour>\\d\\d):(?<min>\\d\\d):(?<sec>\\d\\d)\\.(?<milli>\\d\\d\\d)")

private val PID = Regex("(?<pid>\\d+)")

private val TID = Regex("(?<tid>\\w+)")

private val PRIORITY = Regex("(?<priority>[VDIWEAF])")

private val TAG = Regex("(?<tag>.*?)")

/**
 * Pattern for "logcat -v long" ([ MM-DD HH:MM:SS.mmm PID:TID LEVEL/TAG ]) or "logcat -v long,epoch"
 * header ([ SSSSSSSSSS.mmm PID:TID LEVEL/TAG ]). Example:
 *
 * `[ 08-18 16:39:11.760  2977: 2988 D/PhoneInterfaceManager ]`
 *
 * `[ 1619728495.554  2977: 2988 D/PhoneInterfaceManager ]`
 */
private val HEADER_EPOCH_REGEX = Regex(
  "^\\[ +$EPOCH +$PID: *$TID +$PRIORITY/$TAG +]$"
)

private val HEADER_STANDARD_REGEX = Regex(
  "^\\[ +$DATE +$TIME +$PID: *$TID +$PRIORITY/$TAG +]$"
)


/**
 * Parses a Logcat header from the output of `logcat -v long -v epoch` or `logcat -v long`
 *
 * Header line looks like this:
 * `[          1654196406.333  1722: 2393 I/DisplayPowerController[0] ]`
 *
 * Or:
 * `[ 06-02 19:00:00.710 +0000  1722: 2393 I/DisplayPowerController[0] ]`
 */
internal class LogcatHeaderParser(
  private val format: LogcatFormat,
  private val processNameMonitor: ProcessNameMonitor,
  private val defaultYear: Int = ZonedDateTime.now().year,
  private val defaultZoneId: ZoneId = ZoneId.systemDefault(),
) {

  enum class LogcatFormat(val regex: Regex) {
    EPOCH_FORMAT(HEADER_EPOCH_REGEX),
    STANDARD_FORMAT(HEADER_STANDARD_REGEX),
  }

  /**
   * Parse a header line into a [LogcatHeader] object, or `null` if the input line
   * doesn't match the expected format.
   *
   * @param line   raw text that should be the header line from `logcat -v long` or
   * `logcat -v long,epoch`.
   * @param serialNumber the serial number of the device from which these log messages have been received
   * @return a [LogcatHeader] which represents the passed in text or null if text is not a
   * header.
   */
  fun parseHeader(line: String, serialNumber: String): LogcatHeader? {
    val result = format.regex.matchEntire(line) ?: return null

    val timestamp = when (format) {
      EPOCH_FORMAT -> result.getEpochTimestamp()
      LogcatFormat.STANDARD_FORMAT -> result.getStandardTimestamp(defaultYear, defaultZoneId)
    }

    // We can use `!!.` below because the regex matched so the group must exist
    val groups = result.groups
    val pid = parsePid(groups["pid"]!!.value)
    val processNames = processNameMonitor.getProcessNames(serialNumber, pid)
    val processName = if (pid == 0) "kernel" else processNames?.processName ?: "pid-$pid"
    return LogcatHeader(
      parsePriority(groups["priority"]!!.value),
      pid,
      parseThreadId(groups["tid"]!!.value),
      processNames?.applicationId ?: processName,
      processName,
      groups["tag"]!!.value,
      timestamp
    )
  }

  /**
   * Parses the [priority part of a logcat message header:](https://developer.android.com/studio/command-line/logcat.html)
   * , the "I" in
   *
   * `[          1517949446.554  2848: 2848 I/MainActivity ]`
   *
   * @return the log level corresponding to the priority. If the argument is not one of the
   *     expected letters returns LogLevel.WARN.
   */
  private fun parsePriority(string: String): LogLevel {
    val priority = LogLevel.getByLetter(string)
    if (priority != null) {
      return priority
    }
    if (string != "F") {
      return WARN
    }
    return ASSERT
  }
}

private fun MatchResult.getEpochTimestamp(): Instant {
  // We can use `!!.` below because the regex matched so the group must exist
  return Instant.ofEpochSecond(
    parseEpochSeconds(groups["epochSec"]!!.value),
    MILLISECONDS.toNanos(groups["epochMilli"]?.value!!.toLong()))
}

private fun MatchResult.getStandardTimestamp(defaultYear: Int, defaultZoneId: ZoneId): Instant {
  // We can use `!!.` below because the regex matched so the group must exist
  return Instant.from(
    ZonedDateTime.of(
      defaultYear,
      groups["month"]!!.value.toInt(),
      groups["day"]!!.value.toInt(),
      groups["hour"]!!.value.toInt(),
      groups["min"]!!.value.toInt(),
      groups["sec"]!!.value.toInt(),
      MILLISECONDS.toNanos(groups["milli"]!!.value.toLong()).toInt(),
      defaultZoneId
    )
  )
}

// Epoch seconds has a pattern of `\\d+` and might throw if there are too many digits
private fun parseEpochSeconds(string: String): Long {
  return try {
    string.toLong()
  }
  catch (exception: NumberFormatException) {
    0
  }
}

// Pid has a pattern `\\d+` and might throw if there are too many digits
private fun parsePid(string: String): Int {
  return try {
    string.toInt()
  }
  catch (exception: NumberFormatException) {
    -1
  }
}

// Some versions of logcat return hexadecimal thread IDs. Propagate them as decimal.
private fun parseThreadId(string: String): Int {
  return try {
    Integer.decode(string)
  }
  catch (exception: NumberFormatException) {
    -1
  }
}
