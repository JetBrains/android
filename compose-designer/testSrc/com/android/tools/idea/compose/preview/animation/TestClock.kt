/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.TransitionInfo

enum class ClockType {
  DEFAULT,
  WITH_TRANSITIONS,
  WITH_COORDINATION
}

internal fun ClockType.getClock(): TestClock =
  when (this) {
    ClockType.DEFAULT -> TestClock()
    ClockType.WITH_TRANSITIONS -> TestClockWithTransitions()
    ClockType.WITH_COORDINATION -> TestClockWithCoordination()
  }

/** [TestClock] with available [setClockTimes] method. */
internal open class TestClockWithCoordination : TestClockWithTransitions() {
  fun setClockTimes(clockTimeMillis: Map<ComposeAnimation, Long>) {}
}

/** [TestClock] with available [getTransitions] method. */
internal open class TestClockWithTransitions : TestClock() {
  open fun getTransitions(animation: Any, clockTimeMsStep: Long) =
    listOf(
      TransitionInfo(
        "Int",
        "specType",
        startTimeMillis = 0,
        endTimeMillis = 100,
        values = mapOf(0L to 1, 50L to 2, 100L to 3)
      ),
      TransitionInfo(
        "IntSnap",
        "Snap",
        startTimeMillis = 0,
        endTimeMillis = 0,
        values = mapOf(0L to 100)
      ),
      TransitionInfo(
        "Float",
        "specType",
        startTimeMillis = 100,
        endTimeMillis = 200,
        values = mapOf(100L to 1f, 150L to 0f, 200L to 2f)
      ),
      TransitionInfo(
        "Double",
        "specType",
        startTimeMillis = 0,
        endTimeMillis = 100,
        values = mapOf(0L to 1.0, 50L to 10.0, 100L to 2.0)
      )
    )
}

/**
 * Fake class with methods matching PreviewAnimationClock method signatures, so the code doesn't
 * break when the test tries to call them via reflection.
 */
internal open class TestClock {

  @JvmInline
  internal value class AnimatedVisibilityState private constructor(val value: String) {
    override fun toString() = value

    companion object {
      val Enter = AnimatedVisibilityState("Enter")
      val Exit = AnimatedVisibilityState("Exit")
    }
  }
  open fun getAnimatedProperties(animation: Any) =
    listOf<ComposeAnimatedProperty>(
      ComposeAnimatedProperty("Int", 1),
      ComposeAnimatedProperty("IntSnap", 1),
      ComposeAnimatedProperty("Float", 1f),
      ComposeAnimatedProperty("Double", 1.0)
    )

  fun getMaxDuration() = 1000L
  fun getMaxDurationPerIteration() = 1000L
  fun updateAnimationStates() {}
  fun updateSeekableAnimation(animation: Any, fromState: Any, toState: Any) {}
  fun setClockTime(time: Long) {}
  open fun updateAnimatedVisibilityState(animation: Any, state: Any) {}
  open fun `getAnimatedVisibilityState-xga21d`(animation: Any): Any = "Enter"

  open fun updateFromAndToStates(animation: ComposeAnimation, fromState: Any, toState: Any) {}
}
