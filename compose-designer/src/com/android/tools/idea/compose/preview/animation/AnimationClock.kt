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

import java.lang.reflect.Method

/**
 * Wraps a `PreviewAnimationClock` and adds APIs to make it easier to call the clock's functions via reflection.
 *
 * @param clock Instance of `PreviewAnimationClock` that animations in the inspector are subscribed to.
 */
internal class AnimationClock(val clock: Any) {

  /**
   * Function `getAnimatedProperties` of [clock].
   */
  val getAnimatedPropertiesFunction = findClockFunction("getAnimatedProperties")

  /**
   * Function `getMaxDuration` of [clock].
   */
  val getMaxDurationFunction = findClockFunction("getMaxDuration")

  /**
   * Function `getMaxDurationPerIteration` of [clock].
   */
  val getMaxDurationPerIteration = findClockFunction( "getMaxDurationPerIteration")

  /**
   * Function `setClockTime` of [clock].
   */
  val setClockTimeFunction = findClockFunction("setClockTime")

  /**
   * Function `updateAnimationStates` of [clock].
   */
  val updateAnimationStatesFunction = findClockFunction("updateAnimationStates")

  /**
   * Function `updateSeekableAnimation` of [clock].
   */
  val updateSeekableAnimationFunction = findClockFunction("updateSeekableAnimation")

  private fun findClockFunction(functionName: String): Method =
    clock::class.java.methods.single { it.name == functionName }.apply { isAccessible = true }
}