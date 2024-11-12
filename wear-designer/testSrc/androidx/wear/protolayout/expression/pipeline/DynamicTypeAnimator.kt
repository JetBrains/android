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
package androidx.wear.protolayout.expression.pipeline

/** Interface that should match the one from the AndroidX library */
interface DynamicTypeAnimator {

  interface TypeEvaluator<T> {
    fun evaluate(fraction: Float, startValue: T, endValue: T): T
  }

  val typeEvaluator: TypeEvaluator<*>

  /**
   * Sets the float values that this animation will animate between.
   *
   * @param values The float values to animate between.
   * @throws IllegalArgumentException if this [DynamicTypeAnimator] is not configured with a
   *   suitable [TypeEvaluator] for float values (e.g., [FloatEvaluator]).
   */
  fun setFloatValues(vararg values: Float)

  /**
   * Sets the integer values that this animation will animate between.
   *
   * @param values The integer values to animate between.
   * @throws IllegalArgumentException if this [DynamicTypeAnimator] is not configured with a
   *   suitable [TypeEvaluator] for integer values (e.g., [IntEvaluator] or [ ]).
   */
  fun setIntValues(vararg values: Int)

  /**
   * Advances the animation to the specified time.
   *
   * @param newTime The new time in milliseconds from animation start.
   */
  fun advanceToAnimationTime(newTime: Long)

  /**
   * Gets the start value of the animation.
   *
   * @return The start value of the animation or null if value wasn't set.
   */
  fun getStartValue(): Any?

  /**
   * Gets the end value of the animation.
   *
   * @return The end value of the animation.
   */
  fun getEndValue(): Any?

  /**
   * Gets the last value of the animated property at the current time in the animation.
   *
   * @return The last calculated animated value or null if value wasn't set.
   */
  fun getCurrentValue(): Any?

  /**
   * Gets the duration of the animation, in milliseconds.
   *
   * @return The duration of the animation.
   */
  fun getDurationMs(): Long

  /**
   * Gets the start delay of the animation, in milliseconds.
   *
   * @return The start delay of the animation.
   */
  fun getStartDelayMs(): Long

  /**
   * Returns whether this animator uses the outputs of previous animators as its input. This is
   * typically used to chain animations together, where the result of one animation is used as the
   * starting point for the next. A single animation in a chain is also considered terminal.
   *
   * @return `true` if this animator uses previous animator outputs as its input, `false` otherwise.
   */
  fun isTerminal(): Boolean
}
