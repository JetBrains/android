/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

import java.time.Duration

/**
 * Wraps frame per second calculation functionality. [resetAndStart] has to be explicitly called to
 * start tracking the period of time for FPS calculation.
 */
class FpsCalculator(private val timeNanosProvider: () -> Long) {
  private var startTimeNanos = 0L
  private var frameCounter = 0L

  @Synchronized
  fun resetAndStart() {
    startTimeNanos = timeNanosProvider()
    frameCounter = 0
  }

  @Synchronized
  fun getFps(): Int {
    val timePeriodNanos = timeNanosProvider() - startTimeNanos
    if (timePeriodNanos <= 0) {
      return 0
    }
    return (frameCounter * 1000_000_000 / timePeriodNanos).toInt()
  }

  @Synchronized
  fun getDurationMs(): Int {
    return Duration.ofNanos(timeNanosProvider() - startTimeNanos).toMillis().toInt()
  }

  @Synchronized
  fun incrementFrameCounter() {
    ++frameCounter
  }
}
