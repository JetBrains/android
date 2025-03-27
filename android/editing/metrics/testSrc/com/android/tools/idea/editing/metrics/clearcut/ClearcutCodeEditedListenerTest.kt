/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editing.metrics.clearcut

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.editing.metrics.CodeEdited
import com.android.tools.idea.editing.metrics.Source
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ClearcutCodeEditedListenerTest {
  private val WINDOW_DURATION = 3.minutes
  private val testClock = TestClock()
  private val listener = ClearcutCodeEditedListener(WINDOW_DURATION, testClock)
  private val testUsageTracker = TestUsageTracker(VirtualTimeScheduler())

  private val events =
    listOf(
      CodeEdited(0, 2, Source.CODE_COMPLETION),
      CodeEdited(3, 4, Source.AI_CODE_GENERATION),
      CodeEdited(5, 0, Source.UNKNOWN),
      CodeEdited(27, 5, Source.CODE_COMPLETION),
      CodeEdited(7, 8, Source.REFACTORING),
      CodeEdited(0, 10, Source.USER_PASTE),
      CodeEdited(42, 0, Source.CODE_COMPLETION),
      CodeEdited(17, 3, Source.AI_CODE_GENERATION),
      CodeEdited(-5, 12, Source.IDE_ACTION),
      CodeEdited(0, 0, Source.IDE_ACTION),
      CodeEdited(13, 0, Source.PASTE_FROM_AI_CHAT),
    )

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(testUsageTracker)
  }

  @Test
  fun doesNothingIfDisposed() {
    listener.dispose()

    for (event in events) {
      listener.onCodeEdited(event)
    }

    assertThat(testUsageTracker.usages).isEmpty()
  }

  @Test
  fun doesNotLogUntilWindowDurationPassed() {
    testClock += WINDOW_DURATION
    for (event in events) {
      listener.onCodeEdited(event)
    }

    assertThat(testUsageTracker.usages).isEmpty()

    testClock += 1.milliseconds

    listener.onCodeEdited(CodeEdited(99, 99, Source.UNKNOWN))

    assertThat(testUsageTracker.usages).hasSize(1)
    with(testUsageTracker.usages.single().studioEvent) {
      assertThat(kind).isEqualTo(AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT)
      assertThat(hasEditingMetricsEvent()).isTrue()
      assertThat(editingMetricsEvent.hasCharacterMetrics()).isTrue()
      with(editingMetricsEvent.characterMetrics) {
        assertThat(durationMs).isEqualTo(WINDOW_DURATION.inWholeMilliseconds)
        assertThat(charsAddedList.map { it.source })
          .containsExactlyElementsIn(
            events.filter { it.addedCharacterCount > 0 }.map { it.source.toProto() }.distinct()
          )
        assertThat(charsDeletedList.map { it.source })
          .containsExactlyElementsIn(
            events.filter { it.deletedCharacterCount > 0 }.map { it.source.toProto() }.distinct()
          )
      }
    }
  }

  @Test
  fun logsOnDisposal() {
    for (event in events) {
      listener.onCodeEdited(event)
    }

    assertThat(testUsageTracker.usages).isEmpty()

    listener.dispose()

    assertThat(testUsageTracker.usages).hasSize(1)
    with(testUsageTracker.usages.single().studioEvent) {
      assertThat(kind).isEqualTo(AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT)
      assertThat(hasEditingMetricsEvent()).isTrue()
      assertThat(editingMetricsEvent.hasCharacterMetrics()).isTrue()
      with(editingMetricsEvent.characterMetrics) {
        assertThat(durationMs).isEqualTo(0L)
        assertThat(charsAddedList.map { it.source })
          .containsExactlyElementsIn(
            events.filter { it.addedCharacterCount > 0 }.map { it.source.toProto() }.distinct()
          )
        assertThat(charsDeletedList.map { it.source })
          .containsExactlyElementsIn(
            events.filter { it.deletedCharacterCount > 0 }.map { it.source.toProto() }.distinct()
          )
      }
    }
  }

  @Test
  fun combinesValuesCorrectly() {
    for (event in events) {
      listener.onCodeEdited(event)
    }

    testClock += WINDOW_DURATION + 1.milliseconds
    listener.onCodeEdited(CodeEdited(99, 99, Source.UNKNOWN))

    assertThat(testUsageTracker.usages).hasSize(1)
    with(testUsageTracker.usages.single().studioEvent) {
      assertThat(kind).isEqualTo(AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT)
      assertThat(hasEditingMetricsEvent()).isTrue()
      assertThat(editingMetricsEvent.hasCharacterMetrics()).isTrue()
      with(editingMetricsEvent.characterMetrics) {
        assertThat(durationMs).isEqualTo(WINDOW_DURATION.inWholeMilliseconds)

        assertThat(charsAddedList.associateBy({ it.source }, { it.count }))
          .isEqualTo(
            events
              .filter { it.addedCharacterCount > 0 }
              .groupBy { it.source.toProto() }
              .mapValues { (_, value) -> value.sumOf { it.addedCharacterCount.toLong() } }
          )
        assertThat(charsDeletedList.associateBy({ it.source }, { it.count }))
          .isEqualTo(
            events
              .filter { it.deletedCharacterCount > 0 }
              .groupBy { it.source.toProto() }
              .mapValues { (_, value) -> value.sumOf { it.deletedCharacterCount.toLong() } }
          )

        assertThat(charsDeletedList.map { it.source })
          .containsExactlyElementsIn(
            events.filter { it.deletedCharacterCount > 0 }.map { it.source.toProto() }.distinct()
          )
      }
    }
  }

  private class TestClock(epochMillis: Long = 0L) : Clock {
    private var currentInstant = Instant.fromEpochMilliseconds(epochMillis)

    override fun now() = currentInstant

    operator fun plusAssign(duration: Duration) {
      currentInstant += duration
    }
  }
}
