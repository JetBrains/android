/*
 * Copyright (C) 2021 The Android Open Source Project
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

import java.util.SortedMap

private const val CURVES_SIMILARITY = 0.015

/**
 * Represents animation information for one property for example Color, Dp, Offset. It includes:
 * * [startMs] start point of the animation
 * * [endMs] end point of the animation
 * * [grouped] true if all components have the same curve, in that case [dimension] is 1 and
 *   [components] only contain the first component, regardless of the original number of the
 *   [components].
 * * [dimension] of the property, for example 4 for Rect or Color, 2 for Offset
 * * [components] animation information for each of the components of the property for example for
 *   Rect it includes [AnimatedComponent] for left, top, right, bottom components. Each of the
 *   [AnimatedComponent] includes
 *
 * ```
 *    * [AnimatedComponent.maxValue] and [AnimatedComponent.minValue] of that component
 *    * [AnimatedComponent.linkToNext] true if it's not the last component
 *    * [AnimatedComponent.points] the animation information for this component - map of ms to component value
 * ```
 */
class AnimatedProperty<A>
private constructor(
  val startMs: Int,
  val endMs: Int,
  val grouped: Boolean,
  val components: List<AnimatedComponent<A>>,
  val dimension: Int
) where A : Number, A : Comparable<A> {

  class AnimatedComponent<A>(
    val maxValue: A,
    val minValue: A,
    val linkToNext: Boolean,
    val points: SortedMap<Int, A>
  ) where A : Number, A : Comparable<A>

  // Transforms list of [ComposeUnit.Unit<*>] to list of it components.
  // For example for ComposeUnit.Rect
  //             Component 1
  // List        |      Component 2
  // of          |      |      Component 3
  // units       |      |      |      Component 4
  // ↓           ↓      ↓      ↓      ↓
  // Rect 1    | 1 |  | 2 |  | 3 |  | 4 |  ⬅ point 1
  // Rect 2    | 1 |  | 2 |  | 3 |  | 4 |  ⬅ point 2
  // Rect 3    | 1 |  | 2 |  | 3 |  | 4 |  ⬅ point 3
  // Rect 4    | 1 |  | 2 |  | 3 |  | 4 |  ⬅ point 4
  class Builder {
    /**
     * Animation values - mapping of the animation time in milliseconds to a value of animation for
     * this property - a [ComposeUnit.Unit<*>].
     */
    private val units: MutableMap<Int, ComposeUnit.NumberUnit<*>> = mutableMapOf()
    private var _startTimeMs: Int? = null
    private var _endTimeMs: Int? = null
    fun add(ms: Int, property: ComposeUnit.NumberUnit<*>): Builder {
      units[ms] = property
      return this
    }

    fun setStartTimeMs(startTimeMs: Int): Builder {
      _startTimeMs = startTimeMs
      return this
    }

    fun setEndTimeMs(endTimeMs: Int): Builder {
      _endTimeMs = endTimeMs
      return this
    }

    fun build(): AnimatedProperty<Double>? {
      // Check start and end points
      val startMs = _startTimeMs ?: units.keys.minOrNull() ?: return null
      val endMs = _endTimeMs ?: units.keys.maxOrNull() ?: return null
      // Check all dimensions are correct
      var dimension = units.values.first().components.size
      if (units.values.any { it.components.size != dimension }) return null
      // Check all types are the same
      val valueClass = units.values.first()::class
      if (units.values.any { it::class != valueClass }) return null
      // Check all max and min values are correct
      val maxValues: List<Double> =
        List(dimension) { index -> units.values.map { it.componentAsDouble(index) }.maxOrNull() }
          .filterNotNull()
      val minValues: List<Double> =
        List(dimension) { index -> units.values.map { it.componentAsDouble(index) }.minOrNull() }
          .filterNotNull()
      if (maxValues.size != dimension && minValues.size != dimension) return null
      // Check if values could be grouped.
      // Values could be grouped if the maximum difference between normalized to [0, 1] curves is
      // less than CURVES_SIMILARITY threshold.
      var grouped: Boolean =
        units
          .mapValues {
            Array(dimension) { index ->
              val d = maxValues[index] - minValues[index]
              if (d == 0.0) 0.0 else (it.value.componentAsDouble(index) - minValues[index]) / d
            }
          }
          .all {
            val max = it.value.maxOrNull()
            val min = it.value.minOrNull()
            if (max == null || min == null) return@all false
            max - min <= CURVES_SIMILARITY
          }
      if (grouped) dimension = 1
      return AnimatedProperty(
        startMs = startMs,
        endMs = endMs,
        grouped = grouped,
        components =
          List(dimension) { index ->
            AnimatedComponent(
              maxValue = maxValues[index],
              minValue = minValues[index],
              linkToNext = index != dimension - 1,
              points = units.mapValues { it.value.componentAsDouble(index) }.toSortedMap()
            )
          },
        dimension = dimension
      )
    }
  }
}
