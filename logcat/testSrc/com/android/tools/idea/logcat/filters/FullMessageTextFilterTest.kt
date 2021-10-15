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

import com.android.ddmlib.Log.LogLevel.WARN
import com.android.tools.idea.logcat.logCatMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val TIMESTAMP = Instant.ofEpochMilli(1000)

/**
 * Tests for [FullMessageTextFilter]
 */
class FullMessageTextFilterTest {

  @Test
  fun filter_allText() {
    val message1 = logCatMessage(WARN, pid = 1, tid = 2, "app", "tag", TIMESTAMP, "message")
    val message2 = logCatMessage(WARN, pid = 2, tid = 2, "app", "tag", TIMESTAMP, "message")
    val messages = listOf(message1, message2)

    assertThat(fullMessageTextFilter("1970-01-01 04:00:01.000 1-2 tag app W message").filter(messages)).containsExactly(message1)
  }

  @Test
  fun filter_partialText() {
    val message1 = logCatMessage(WARN, pid = 1, tid = 2, "app", "tag1", TIMESTAMP, "message")
    val message2 = logCatMessage(WARN, pid = 1, tid = 2, "app", "tag", TIMESTAMP, "message")
    val message3 = logCatMessage(WARN, pid = 2, tid = 2, "app", "tag", TIMESTAMP, "message")
    val message4 = logCatMessage(WARN, pid = 2, tid = 2, "app", "tag1", TIMESTAMP, "message")
    val messages = listOf(message1, message2, message3, message4)

    assertThat(fullMessageTextFilter("tag ap").filter(messages)).containsExactly(message2, message3).inOrder()
  }

  @Test
  fun filter_ignoreCase() {
    val message1 = logCatMessage(WARN, pid = 1, tid = 2, "APP", "tag", TIMESTAMP, "message")
    val message2 = logCatMessage(WARN, pid = 2, tid = 2, "app", "TAG", TIMESTAMP, "message")
    val messages = listOf(message1, message2)

    assertThat(fullMessageTextFilter("tag ap").filter(messages)).containsExactly(message1, message2).inOrder()
  }
}

private fun fullMessageTextFilter(text: String) = FullMessageTextFilter(text, ZoneId.of("Asia/Yerevan"))
