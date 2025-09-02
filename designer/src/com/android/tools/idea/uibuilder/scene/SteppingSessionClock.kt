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
package com.android.tools.idea.uibuilder.scene

import kotlin.time.Duration

/**
 * A [SessionClock] implementation that increments its time by a fixed step on every [getTimeNanos]
 * call, unless it is paused.
 *
 * @param step The amount to increment the time by on each un-paused call to [getTimeNanos].
 */
class SteppingSessionClock(private val step: Duration) : SessionClock {
  private var currentTimeNanos: Long = 0L
  private var isPaused: Boolean = false

  /**
   * If the clock is not paused, increments the current time by [step] and returns the new time. If
   * the clock is paused, it returns the current time without incrementing it.
   *
   * @return The current time in nanoseconds.
   */
  override fun getTimeNanos(): Long {
    if (!isPaused) {
      currentTimeNanos += step.inWholeNanoseconds
    }
    return currentTimeNanos
  }

  /** Pauses the clock. Subsequent calls to [getTimeNanos] will not increment the time. */
  override fun pause() {
    isPaused = true
  }

  /** Resumes the clock. Subsequent calls to [getTimeNanos] will increment the time again. */
  override fun resume() {
    isPaused = false
  }
}
