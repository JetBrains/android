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
import java.lang.reflect.Method
import org.jetbrains.annotations.VisibleForTesting

/**
 * Returns true if coordination is supported by the clock. In that case clock time could be set
 * individually for each animation.
 */
internal fun AnimationClock.coordinationIsSupported() = setClockTimesFunction != null

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
internal fun AnimationClock.setClockTime(clockTimeMillis: Long) {
  setClockTimeFunction.invoke(clock, clockTimeMillis)
}

/** Seeks each animation being tracked to the given [clockTimeMillis]. */
internal fun AnimationClock.setClockTimes(clockTimeMillis: Map<ComposeAnimation, Long>) {
  setClockTimesFunction?.invoke(clock, clockTimeMillis)
}

/**
 * Updates the TransitionState corresponding to the given [ComposeAnimation] in the transitionStates
 * map, creating a TransitionState with the given [fromState] and [toState].
 */
internal fun AnimationClock.updateFromAndToStates(
  animation: ComposeAnimation,
  fromState: Any,
  toState: Any
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
 * to.
 */
class AnimationClock(val clock: Any) {

  /** Function `getAnimatedProperties` of [clock]. */
  val getAnimatedPropertiesFunction by lazy { findClockFunction("getAnimatedProperties") }

  /**
   * Function `getTransitions` of [clock]. This API was added in Compose UI Tooling 1.1.0-alpha05.
   * For early versions `getAnimatedProperties` should be called instead. The caller should first
   * check if this method exists.
   */
  val getTransitionsFunction: Method? by lazy { findClockFunctionIfExists("getTransitions") }

  /** Function `getMaxDuration` of [clock]. */
  val getMaxDurationFunction by lazy { findClockFunction("getMaxDuration") }

  /** Function `getMaxDurationPerIteration` of [clock]. */
  val getMaxDurationPerIteration by lazy { findClockFunction("getMaxDurationPerIteration") }

  /** Function `setClockTime` of [clock]. */
  val setClockTimeFunction by lazy { findClockFunction("setClockTime") }

  /** Function `setClockTimes` of [clock]. */
  val setClockTimesFunction by lazy { findClockFunctionIfExists("setClockTimes") }

  /** Function `updateFromAndToStates` of [clock]. */
  val updateFromAndToStatesFunction by lazy { findClockFunction("updateFromAndToStates") }

  /**
   * Function `updateAnimatedVisibilityState` of [clock]. This API was added in Compose
   * 1.1.0-alpha04, and trying to call it when using early Compose versions will cause a crash and
   * the corresponding preview will not render properly. In order to make sure this method exists,
   * the caller should first check if the animation type is
   * ComposeAnimationType.ANIMATED_VISIBILITY, which was also introduced in the same Compose version
   * and represents the animation type for which this method makes sense to be called.
   */
  val updateAnimatedVisibilityStateFunction by lazy {
    findClockFunction("updateAnimatedVisibilityState")
  }

  /**
   * Function `getAnimatedVisibilityState` of [clock]. This API was added in Compose 1.1.0-alpha04,
   * and trying to call it when using early Compose versions will cause a crash and the
   * corresponding preview will not render properly. In order to make sure this method exists, the
   * caller should first check if the animation type is ComposeAnimationType.ANIMATED_VISIBILITY,
   * which was also introduced in the same Compose version and represents the animation type for
   * which this method makes sense to be called.
   */
  val getAnimatedVisibilityStateFunction by lazy { findClockFunction("getAnimatedVisibilityState") }

  @VisibleForTesting
  fun findClockFunction(functionName: String): Method = findClockFunctionIfExists(functionName)!!

  private fun findClockFunctionIfExists(functionName: String): Method? =
    clock::class.java.methods
      .firstOrNull() {
        // Convert something like `setClockTime-zrx7VqY` into `setClockTime` in order to handle
        // methods that use inline classes.
        // See https://kotlinlang.org/docs/inline-classes.html#mangling for more info.
        val normalizedName = it.name.substringBefore('-')
        normalizedName == functionName
      }
      ?.apply { isAccessible = true }
}
