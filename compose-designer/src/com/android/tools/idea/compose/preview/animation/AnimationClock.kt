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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import org.jetbrains.annotations.VisibleForTesting
import java.lang.reflect.Method

/**
 * Returns a list of the given [ComposeAnimation]'s animated properties. The properties are wrapped
 * into a [ComposeAnimatedProperty] object containing the property label and the corresponding value
 * at the current time.
 */
internal fun AnimationClock.getAnimatedProperties(animation: ComposeAnimation) =
  getAnimatedPropertiesFunction.invoke(clock, animation) as List<ComposeAnimatedProperty>

/** Returns the duration (ms) of the longest animation being tracked. */
internal fun AnimationClock.getMaxDurationMs(): Long = getMaxDurationFunction.invoke(clock) as Long

/**
 * Returns the longest duration (ms) per iteration among the animations being tracked. This can be
 * different from [getMaxDurationMs], for instance, when there is one or more repeatable animations
 * with multiple iterations.
 */
internal fun AnimationClock.getMaxDurationMsPerIteration(): Long {
  return getMaxDurationPerIteration.invoke(clock) as Long
}

/** Seeks each animation being tracked to the given [clockTimeMillis]. */
internal fun AnimationClock.setClockTimes(clockTimeMillis: Map<ComposeAnimation, Long>) {
  setClockTimesFunction.invoke(clock, clockTimeMillis)
}

/**
 * Updates the TransitionState corresponding to the given [ComposeAnimation] in the transitionStates
 * map, creating a TransitionState with the given [fromState] and [toState].
 *
 * [fromState] and [toState] being list is the expectation on androidx, i.e. it will always receive
 * a list and try to parse it accordingly. Values passed here are always wrapped into a list: either
 * a singleton list containing a 1-dimension value, or the list of components for n-dimension
 * values.
 */
internal fun AnimationClock.updateFromAndToStates(
  animation: ComposeAnimation,
  fromState: List<Any?>,
  toState: List<Any?>,
) {
  updateFromAndToStatesFunction.invoke(clock, animation, fromState, toState)
}

/** Updates the given [ComposeAnimation]'s cached AnimatedVisibilityState with the given [state]. */
internal fun AnimationClock.updateAnimatedVisibilityState(animation: ComposeAnimation, state: Any) {
  updateAnimatedVisibilityStateFunction.invoke(clock, animation, state)
}

/**
 * Returns the cached AnimatedVisibilityState corresponding to the given [ComposeAnimation] object.
 * Falls back to AnimatedVisibilityState.Enter if there is no state currently mapped to the
 * [ComposeAnimation].
 */
internal fun AnimationClock.getAnimatedVisibilityState(animation: ComposeAnimation): Any =
  getAnimatedVisibilityStateFunction.invoke(clock, animation)

/**
 * Wraps a `PreviewAnimationClock` and adds APIs to make it easier to call the clock's functions via
 * reflection.
 *
 * @param clock Instance of `PreviewAnimationClock` that animations in the inspector are subscribed
 *   to.
 */
class AnimationClock(val clock: Any) {

  /** Function `getAnimatedProperties` of [clock]. */
  val getAnimatedPropertiesFunction by lazy { findClockFunction("getAnimatedProperties") }

  /** Function `getTransitions` of [clock]. */
  val getTransitionsFunction: Method by lazy { findClockFunction("getTransitions") }

  /** Function `getMaxDuration` of [clock]. */
  val getMaxDurationFunction by lazy { findClockFunction("getMaxDuration") }

  /** Function `getMaxDurationPerIteration` of [clock]. */
  val getMaxDurationPerIteration by lazy { findClockFunction("getMaxDurationPerIteration") }

  /** Function `setClockTimes` of [clock]. */
  val setClockTimesFunction by lazy { findClockFunction("setClockTimes") }

  /** Function `updateFromAndToStates` of [clock]. */
  val updateFromAndToStatesFunction by lazy { findClockFunction("updateFromAndToStates") }

  /** Function `updateAnimatedVisibilityState` of [clock]. */
  val updateAnimatedVisibilityStateFunction by lazy {
    findClockFunction("updateAnimatedVisibilityState")
  }

  /** Function `getAnimatedVisibilityState` of [clock]. */
  val getAnimatedVisibilityStateFunction by lazy { findClockFunction("getAnimatedVisibilityState") }

  @VisibleForTesting
  fun findClockFunction(functionName: String): Method =
    clock::class
      .java
      .methods
      .firstOrNull {
        // Convert something like `setClockTime-zrx7VqY` into `setClockTime` in order to handle
        // methods that use inline classes.
        // See https://kotlinlang.org/docs/inline-classes.html#mangling for more info.
        val normalizedName = it.name.substringBefore('-')
        normalizedName == functionName
      }
      ?.apply { isAccessible = true }!!
}
