/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.ThrowableComputable
import java.util.ArrayList
import kotlin.math.pow
import kotlin.math.sqrt

private const val NUMBER_OF_WARM_UP = 2
private const val NUMBER_OF_SAMPLES = 40
private const val MAX_PRUNED_SAMPLES = NUMBER_OF_SAMPLES / 4

private data class MetricStats(val stdDev: Double, val average: Double)

/**
 * Try to prune outliers based on standard deviation. No more than [.MAX_PRUNED_SAMPLES] samples will be pruned.
 */
private fun List<MetricSample>.pruneOutliers(): List<MetricSample> {
  val (stdDev, average) = standardDeviationAndAverage()
  val lowerBound = (average - stdDev).toLong()
  val upperBound = (average + stdDev).toLong()
  val prunedList: List<MetricSample> = filter { it.sampleData in lowerBound..upperBound }
  // Too many samples pruned (i.e. data is widely spread). Return the raw list.
  return if (prunedList.size < size - MAX_PRUNED_SAMPLES) {
    this
  }
  else prunedList
}

private fun List<MetricSample>.standardDeviationAndAverage(): MetricStats {
  val average = map { it.sampleData }.average()
  return MetricStats(sqrt(map { (it.sampleData - average).pow(2.0) }.sum() / size), average)
}

private val renderTimeBenchmark = Benchmark.Builder("DesignTools Render Time Benchmark")
  .setDescription("Base line for RenderTask inflate & render time (mean) after $NUMBER_OF_SAMPLES samples.")
  .build()
private val renderMemoryBenchmark = Benchmark.Builder("DesignTools Memory Usage Benchmark")
  .setDescription("Base line for RenderTask memory usage (mean) after $NUMBER_OF_SAMPLES samples.")
  .build()

internal object PerfgateRenderUtil {
  @JvmStatic
  @Throws(Exception::class)
  fun computeAndRecordMetric(
    renderMetricName: String,
    memoryMetricName: String,
    computable: ThrowableComputable<PerfgateRenderMetric, out Exception?>) {
    System.gc()
    // LayoutLib has a large static initialization that would trigger on the first render.
    // Warm up by inflating few times before measuring.
    repeat(NUMBER_OF_WARM_UP) {
      computable.compute()
    }

    // baseline samples
    val renderTimes: MutableList<MetricSample> = ArrayList(NUMBER_OF_SAMPLES)
    val memoryUsages: MutableList<MetricSample> = ArrayList(NUMBER_OF_SAMPLES)
    repeat(NUMBER_OF_SAMPLES) {
      val metric = computable.compute()
      renderTimes.add(metric.renderTimeMetricSample)
      memoryUsages.add(metric.memoryMetricSample)
    }

    Metric(renderMetricName).apply {
      addSamples(renderTimeBenchmark, *renderTimes.pruneOutliers().toTypedArray())
      commit()
    }
    // Let's start without pruning to see how bad it is.
    Metric(memoryMetricName).apply {
      addSamples(renderMemoryBenchmark, *memoryUsages.toTypedArray())
      commit()
    }
  }

  @JvmStatic
  fun getInflateMetric(task: RenderTask,
                       resultVerifier: (RenderResult) -> Unit): PerfgateRenderMetric {
    val renderMetric = PerfgateRenderMetric()
    renderMetric.beforeTest()
    val result = Futures.getUnchecked(
      task.inflate())
    renderMetric.afterTest()
    resultVerifier(result)
    return renderMetric
  }

  @JvmStatic
  fun getRenderMetric(task: RenderTask,
                      inflateVerifier: (RenderResult) -> Unit,
                      renderVerifier: (RenderResult) -> Unit): PerfgateRenderMetric {
    inflateVerifier(
      Futures.getUnchecked(task.inflate()))
    val renderMetric = PerfgateRenderMetric()
    renderMetric.beforeTest()
    val result = Futures.getUnchecked(
      task.render())
    renderMetric.afterTest()
    renderVerifier(result)
    return renderMetric
  }

  @JvmStatic
  fun getRenderMetric(task: RenderTask, resultVerifier: (RenderResult) -> Unit): PerfgateRenderMetric =
    getRenderMetric(task, resultVerifier, resultVerifier)
}