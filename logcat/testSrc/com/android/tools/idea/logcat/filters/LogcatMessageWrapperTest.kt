/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.message.LogLevel.WARN
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)
private val ZONE_ID = ZoneId.of("Asia/Yerevan")

/**
 * Tests for [LogcatMessageWrapper]
 */
class LogcatMessageWrapperTest {
  private val logcatMessage = logcatMessage(WARN, pid = 1, tid = 2, "app", "tag", TIMESTAMP, "message")

  @Test
  fun logLine() {
    assertThat(LogcatMessageWrapper(logcatMessage, ZONE_ID).logLine)
      .isEqualTo("1970-01-01 04:00:01.000 1-2 tag app W: message")
  }

  @Test
  fun message() {
    assertThat(LogcatMessageWrapper(logcatMessage, ZONE_ID).logcatMessage).isSameAs(logcatMessage)
  }
}