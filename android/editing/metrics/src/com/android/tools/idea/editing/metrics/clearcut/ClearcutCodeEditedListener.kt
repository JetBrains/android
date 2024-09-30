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
import com.intellij.openapi.Disposable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** [CodeEditedListener] that reports results to Clearcut lazily. */
class ClearcutCodeEditedListener(
  private val windowDuration: Duration = 1.minutes,
  private val clock: Clock = Clock.System,
) : CodeEditedListener, Disposable {
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
      charsAdded.merge(event.source, event.addedCharacterCount.toLong(), Long::plus)
      charsDeleted.merge(event.source, event.deletedCharacterCount.toLong(), Long::plus)
    }
  }

  @Synchronized
  override fun dispose() {
    isDisposed = true
    log(window, clock.now() - window.startTime)
  }

  private fun log(window: Window, elapsed: Duration) {
    val proto = AndroidStudioEvent.newBuilder()
    // TODO(b/354029449): Actually build and log the proto.
    if (false) UsageTracker.log(proto)
  }

  private class Window(val startTime: Instant) {
    val charsAdded: MutableMap<Source, Long> = mutableMapOf()
    val charsDeleted: MutableMap<Source, Long> = mutableMapOf()
  }
}
