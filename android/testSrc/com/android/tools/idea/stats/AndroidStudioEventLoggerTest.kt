/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT
import com.google.wireless.android.sdk.stats.EditorFileType
import com.intellij.internal.statistic.eventLog.EventLogGroup
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val QUICK_DOC_CLOSED = "quick.doc.closed"
private const val FILE_TYPE_KEY = "file_type"
private const val DURATION_MS_KEY = "duration_ms"
private val EVENT_LOG_GROUP = EventLogGroup("documentation", 1)

@RunWith(JUnit4::class)
class AndroidStudioEventLoggerTest {
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  private val testScheduler = TestCoroutineScheduler()
  private val testDispatcher = StandardTestDispatcher(testScheduler)
  private val testScope = TestScope(testDispatcher)
  private val logger = AndroidStudioEventLogger(testScope)

  @Test
  fun logQuickDocEvent_success() {
    val eventFileType = "Kotlin"
    val durationMs = 12345L
    val data = mapOf(FILE_TYPE_KEY to eventFileType, DURATION_MS_KEY to durationMs)

    logger.logAsync(EVENT_LOG_GROUP, QUICK_DOC_CLOSED, data, isState = true)
    assertThat(usageTrackerRule.usages).isEmpty()

    testScheduler.advanceUntilIdle()

    assertThat(usageTrackerRule.usages).hasSize(1)

    with(assertNotNull(usageTrackerRule.usages.single().studioEvent)) {
      assertThat(kind).isEqualTo(EDITING_METRICS_EVENT)
      assertThat(hasEditingMetricsEvent())
      assertThat(editingMetricsEvent.hasQuickDocEvent())
      with(editingMetricsEvent.quickDocEvent) {
        assertThat(fileType).isEqualTo(EditorFileType.KOTLIN)
        assertThat(shownDurationMs).isEqualTo(durationMs)
      }
    }
  }

  @Test
  fun logQuickDocEvent_noFileType() {
    val data = mapOf(DURATION_MS_KEY to 12345L)

    assertFailsWith<java.lang.AssertionError> {
      logger.logAsync(EVENT_LOG_GROUP, QUICK_DOC_CLOSED, data, isState = true)
    }

    testScheduler.advanceUntilIdle()
    assertThat(usageTrackerRule.usages).isEmpty()
  }

  @Test
  fun logQuickDocEvent_fileTypeNotString() {
    val data = mapOf(FILE_TYPE_KEY to 17.5f, DURATION_MS_KEY to 12345L)

    assertFailsWith<java.lang.AssertionError> {
      logger.logAsync(EVENT_LOG_GROUP, QUICK_DOC_CLOSED, data, isState = true)
    }

    testScheduler.advanceUntilIdle()
    assertThat(usageTrackerRule.usages).isEmpty()
  }

  @Test
  fun logQuickDocEvent_noDurationMs() {
    val data = mapOf(FILE_TYPE_KEY to "JAVA")

    assertFailsWith<java.lang.AssertionError> {
      logger.logAsync(EVENT_LOG_GROUP, QUICK_DOC_CLOSED, data, isState = true)
    }

    testScheduler.advanceUntilIdle()
    assertThat(usageTrackerRule.usages).isEmpty()
  }

  @Test
  fun logQuickDocEvent_durationMsNotLong() {
    val data = mapOf(FILE_TYPE_KEY to "JAVA", DURATION_MS_KEY to 12345)

    assertFailsWith<java.lang.AssertionError> {
      logger.logAsync(EVENT_LOG_GROUP, QUICK_DOC_CLOSED, data, isState = true)
    }

    testScheduler.advanceUntilIdle()
    assertThat(usageTrackerRule.usages).isEmpty()
  }
}
