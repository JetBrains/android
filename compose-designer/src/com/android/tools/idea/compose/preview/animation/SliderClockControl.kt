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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.idea.compose.preview.animation.PlaybackControls.TimelineSpeed
import javax.swing.JSlider

/** Clock control for a [JSlider]. */
class SliderClockControl(val slider: JSlider) {

  /** Speed multiplier of the timeline clock. [TimelineSpeed.X_1] by default (normal speed). */
  var speed: TimelineSpeed = TimelineSpeed.X_1

  /** Whether the timeline should play in loop or stop when reaching the end. */
  var playInLoop = false

  fun isAtStart() = slider.value <= slider.minimum

  fun isAtEnd() = slider.value >= slider.maximum

  fun jumpToStart() {
    slider.value = slider.minimum
  }

  fun jumpToEnd() {
    slider.value = slider.maximum
  }

  fun updateMaxDuration(durationMs: Long) {
    slider.maximum = durationMs.toInt()
  }

  /** Increments the clock by the given value, taking the current [speed] into account. */
  fun incrementClockBy(increment: Int) {
    slider.value += (increment * speed.speedMultiplier).toInt()
  }
}
