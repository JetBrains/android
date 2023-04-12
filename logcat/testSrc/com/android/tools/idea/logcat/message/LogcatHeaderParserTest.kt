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

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.testing.FakeProcessNameMonitor
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.EPOCH_FORMAT
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.STANDARD_FORMAT
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit


/**
 * Tests for [LogcatHeaderParser]
 */
internal class LogcatHeaderParserTest {
  private val fakeProcessNameMonitor = FakeProcessNameMonitor()

  @Test
  fun parseHeader_withEpoch() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1517266949.472 5755:601 I/Tag ]",
        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, 5755, 601, "pid-5755", "pid-5755", "Tag", Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withDateTime() {
    val logCatHeaderParser = logcatHeaderParser(STANDARD_FORMAT, defaultYear = 2022, defaultZoneId = ZoneId.of("Asia/Yerevan"))

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 05-26 14:58:23.972 5755:601 I/Tag ]",
        "device"
      )
    ).isEqualTo(
      LogcatHeader(
        INFO,
        5755,
        601,
        "pid-5755",
        "pid-5755",
        "Tag",
        Instant.from(ZonedDateTime.of(2022, 5, 26, 14, 58, 23, TimeUnit.MILLISECONDS.toNanos(972).toInt(), ZoneId.of("Asia/Yerevan")))
      )
    )
  }

  @Test
  fun parseHeader_withSpaces() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[     1517266949.472     5755:601     I/Tag     ]",
        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, 5755, 601, "pid-5755", "pid-5755", "Tag", Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withHexTid() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1517266949.472 5755:0x100 I/Tag ]",

        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, 5755, 256, "pid-5755", "pid-5755", "Tag", Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withPidZero() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1517266949.472 0:601 I/Tag ]",

        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, 0, 601, "kernel", "kernel", "Tag", Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withProcessNames() {
    fakeProcessNameMonitor.addProcessName("device", 5755, "application-id", "process-name")
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT, fakeProcessNameMonitor)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1517266949.472 5755:601 I/Tag ]",
        "device"
      )
    ).isEqualTo(LogcatHeader(
      INFO,
      5755,
      601,
      "application-id",
      "process-name",
      "Tag",
      Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withInvalidPid() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1517266949.472 1234567890123456789012345678901234567890:601 I/Tag ]",
        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, -1, 601, "pid--1", "pid--1", "Tag", Instant.ofEpochSecond(1517266949, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  @Test
  fun parseHeader_withInvalidEpochSeconds() {
    val logCatHeaderParser = logcatHeaderParser(EPOCH_FORMAT)

    assertThat(
      logCatHeaderParser.parseHeader(
        "[ 1234567890123456789012345678901234567890.472 5755:601 I/Tag ]",
        "device"
      )
    ).isEqualTo(LogcatHeader(INFO, 5755, 601, "pid-5755", "pid-5755", "Tag", Instant.ofEpochSecond(0, TimeUnit.MILLISECONDS.toNanos(472))))
  }

  private fun logcatHeaderParser(
    format: LogcatHeaderParser.LogcatFormat,
    processNameMonitor: ProcessNameMonitor = FakeProcessNameMonitor(),
    defaultYear: Int = 2022,
    defaultZoneId: ZoneId = ZoneId.of("Asia/Yerevan"),

    ) = LogcatHeaderParser(
    format, processNameMonitor, defaultYear, defaultZoneId)
}
