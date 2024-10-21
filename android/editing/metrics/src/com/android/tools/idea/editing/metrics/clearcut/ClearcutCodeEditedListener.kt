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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.editing.metrics.CodeEdited
import com.android.tools.idea.editing.metrics.CodeEditedListener
import com.android.tools.idea.editing.metrics.Source
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditingMetricsEvent.CharacterMetrics
import com.intellij.openapi.Disposable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.annotations.TestOnly

/** [CodeEditedListener] that reports results to Clearcut lazily. */
class ClearcutCodeEditedListener
@TestOnly
internal constructor(private val windowDuration: Duration, private val clock: Clock) :
  CodeEditedListener, Disposable {

  constructor() : this(windowDuration = 1.minutes, clock = Clock.System)

  private var isDisposed = false
  private var window = Window(clock.now())

  @Synchronized
  override fun onCodeEdited(event: CodeEdited) {
    if (isDisposed) return
    val now = clock.now()
    val elapsed = clock.now() - window.startTime
    if (elapsed > windowDuration) {
      log(window, elapsed)
      val newWindowStartTime = now - windowDuration * (elapsed / windowDuration).mod(1.0)
      window = Window(newWindowStartTime)
    }
    with(window) {
      val addedChars = event.addedCharacterCount
      if (addedChars > 0) charsAdded.merge(event.source, addedChars.toLong(), Long::plus)
      val deletedChars = event.deletedCharacterCount
      if (deletedChars > 0) charsDeleted.merge(event.source, deletedChars.toLong(), Long::plus)
    }
  }

  @Synchronized
  override fun dispose() {
    isDisposed = true
    log(window, clock.now() - window.startTime)
  }

  private fun log(window: Window, elapsed: Duration) {
    if (window.charsAdded.isEmpty() && window.charsDeleted.isEmpty()) return
    val event =
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.EDITING_METRICS_EVENT
        editingMetricsEventBuilder.apply {
          setCharacterMetrics(window.toCharacterMetrics(elapsed.coerceAtMost(windowDuration)))
        }
      }
    UsageTracker.log(event)
  }

  private class Window(val startTime: Instant) {
    val charsAdded: MutableMap<Source, Long> = mutableMapOf()
    val charsDeleted: MutableMap<Source, Long> = mutableMapOf()

    fun toCharacterMetrics(elapsed: Duration): CharacterMetrics.Builder =
      CharacterMetrics.newBuilder().apply {
        charsAdded.toSourceCountList().forEach(::addCharsAdded)
        charsDeleted.toSourceCountList().forEach(::addCharsDeleted)
        durationMs = elapsed.inWholeMilliseconds
      }
  }
}

internal fun Source.toProto(): CharacterMetrics.Source =
  when (this) {
    Source.UNKNOWN -> CharacterMetrics.Source.UNKNOWN
    Source.TYPING -> CharacterMetrics.Source.TYPING
    Source.IDE_ACTION -> CharacterMetrics.Source.IDE_ACTION
    Source.USER_PASTE -> CharacterMetrics.Source.PASTE
    Source.REFACTORING -> CharacterMetrics.Source.REFACTORING
    Source.CODE_COMPLETION -> CharacterMetrics.Source.CODE_COMPLETION
    Source.AI_CODE_COMPLETION -> CharacterMetrics.Source.AI_CODE_COMPLETION
    Source.AI_CODE_GENERATION -> CharacterMetrics.Source.AI_CODE_TRANSFORMATION
    Source.PASTE_FROM_AI_CHAT -> CharacterMetrics.Source.PASTE_FROM_AI_CHAT
  }

internal fun Map<Source, Long>.toSourceCountList(): List<CharacterMetrics.SourceCount.Builder> =
  map {
    CharacterMetrics.SourceCount.newBuilder().apply {
      source = it.key.toProto()
      count = it.value
    }
  }
