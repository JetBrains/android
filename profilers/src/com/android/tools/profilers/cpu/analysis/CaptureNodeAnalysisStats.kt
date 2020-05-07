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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.profilers.cpu.CaptureNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Holds statistics for capture node analysis, e.g. count, average.
 */
class CaptureNodeAnalysisStats private constructor() {
  var count = 0L
    private set
  var sum = 0L
    private set
  var min = Long.MAX_VALUE
    private set
  var max = 0L
    private set
  var standardDeviation = 0.0
    private set

  val average: Double
    get() = if (count == 0L) 0.0 else sum.toDouble() / count

  companion object {
    /**
     * Takes a list of [CaptureNode]s and compute their statistics, e.g. standard deviation.
     */
    fun fromNodes(nodes: List<CaptureNode>) = CaptureNodeAnalysisStats().apply {
      // First pass to compute count, min, max and average.
      nodes.forEach {
        val duration = it.duration
        count++
        sum += duration
        min = min(min, duration)
        max = max(max, duration)
      }
      // Second pass to compute standard deviation.
      val avg = average
      val sumOfSquareDiff = nodes.asSequence()
        .map { (it.duration - avg) * (it.duration - avg) }
        .sum()
      standardDeviation = sqrt(sumOfSquareDiff / count)
    }
  }
}