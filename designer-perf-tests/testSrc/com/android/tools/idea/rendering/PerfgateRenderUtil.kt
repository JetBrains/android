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

import com.android.tools.idea.rendering.imagepool.ImagePool
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.google.common.collect.LinkedListMultimap
import com.google.common.math.Quantiles
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.ThrowableComputable
import junit.framework.TestCase
import java.time.Instant
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

/**
 * A measurement for the given [metric]. This class will be called before and after a profiled operation is executed. [after] is expected
 * to return the [MetricSample] of one execution.
 */
interface MetricMeasurement<T> {
  val metric: Metric

  /**
   * Set of [Analyzer] to run on the result after obtaining the sample.
   */
  val analyzers: Set<Analyzer>

  fun before()
  fun after(result: T): MetricSample?
}

abstract class MetricMeasurementAdapter<T>(override val metric: Metric): MetricMeasurement<T> {
  override val analyzers = setOf<Analyzer>()
}

private class DelegateMetricMeasurementWithAnalyzers<T>(private val delegate: MetricMeasurement<T>,
                                                        override val analyzers: Set<Analyzer>) : MetricMeasurementAdapter<T>(delegate.metric) {
  override fun before() = delegate.before()
  override fun after(result: T): MetricSample? = delegate.after(result)
}

/**
 * Returns a [MetricMeasurement] that runs the given [analyzer]s in the result.
 */
internal fun <T> MetricMeasurement<T>.withAnalyzers(analyzers: Set<Analyzer>): MetricMeasurement<T> =
  DelegateMetricMeasurementWithAnalyzers(this, analyzers)

/**
 * Returns a [MetricMeasurement] that runs the given [analyzer] in the result.
 */
internal fun <T> MetricMeasurement<T>.withAnalyzer(analyzer: Analyzer): MetricMeasurement<T> =
  DelegateMetricMeasurementWithAnalyzers(this, setOf(analyzer))

/**
 * A [MetricMeasurement] that measures the elapsed time in milliseconds between [before] and [after].
 */
internal class ElapsedTimeMeasurement<T>(metric: Metric) : MetricMeasurementAdapter<T>(metric) {
  private var startMs = -1L

  override fun before() {
    startMs = System.currentTimeMillis()
  }

  override fun after(result: T) =
    MetricSample(Instant.now().toEpochMilli(), System.currentTimeMillis() - startMs)
}

/**
 * A [MetricMeasurement] that measures the memory usage delta between [before] and [after].
 */
internal class MemoryUseMeasurement<T>(metric: Metric) : MetricMeasurementAdapter<T>(metric) {
  private var initialMemoryUse = -1L

  override fun before() {
    initialMemoryUse = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
  }

  override fun after(result: T) =
    MetricSample(Instant.now().toEpochMilli(),
                 Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - initialMemoryUse)
}


/**
 * A [MetricMeasurement] that measures the inflate time of a render.
 */
internal class InflateTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.inflateDurationMs != -1L)
    MetricSample(Instant.now().toEpochMilli(), result.stats.inflateDurationMs)
  else null // No inflate time available
}

/**
 * A [MetricMeasurement] that measures the render time of a render.
 */
internal class RenderTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.renderDurationMs != -1L)
    MetricSample(Instant.now().toEpochMilli(), result.stats.renderDurationMs)
  else null // No render time available
}

/**
 * A [MetricMeasurement] that measures the render time of a render.
 */
internal class ClassLoadTimeMeasurment(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.totalClassLoadDurationMs != -1L)
    MetricSample(Instant.now().toEpochMilli(), result.stats.totalClassLoadDurationMs)
  else null // No render time available
}

/**
 * A [MetricMeasurement] that measures the render time of a render.
 */
internal class ClassLoadCountMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.classesFound != -1L)
    MetricSample(Instant.now().toEpochMilli(), result.stats.classesFound)
  else null // No render time available
}

/**
 * A [MetricMeasurement] that measures the render time of a render.
 */
internal class ClassAverageLoadTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.totalClassLoadDurationMs != -1L && result.stats.classesFound > 0)
    MetricSample(Instant.now().toEpochMilli(), result.stats.totalClassLoadDurationMs / result.stats.classesFound)
  else null // No render time available
}

/**
 * A [MetricMeasurement] that measures the render time of a render.
 */
internal class ClassRewriteTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result.stats.totalClassRewriteDurationMs != -1L)
    MetricSample(Instant.now().toEpochMilli(), result.stats.totalClassRewriteDurationMs)
  else null // No render time available
}

/**
 * A [MetricMeasurement] that measures the time it takes to execute callbacks the very first time.
 */
internal class FirstCallbacksExecutionTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result is ExtendedRenderResult)
    MetricSample(Instant.now().toEpochMilli(), result.extendedStats.firstExecuteCallbacksDurationMs)
  else null // No time available
}

/**
 * A [MetricMeasurement] that measures the time it takes to propagate the touch event the very first time.
 */
internal class FirstTouchEventTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result is ExtendedRenderResult)
    MetricSample(Instant.now().toEpochMilli(), result.extendedStats.firstInteractionEventDurationMs)
  else null // No time available
}

/**
 * A [MetricMeasurement] that measures the time it takes to execute callbacks after the very first touch event.
 */
internal class PostTouchEventCallbacksExecutionTimeMeasurement(metric: Metric) : MetricMeasurementAdapter<RenderResult>(metric) {
  override fun before() {}

  override fun after(result: RenderResult) = if (result is ExtendedRenderResult)
    MetricSample(Instant.now().toEpochMilli(), result.extendedStats.postInteractionEventDurationMs)
  else null // No time available
}

@Suppress("UnstableApiUsage")
private fun Collection<Long>.median() =
  Quantiles.median().compute(this)

@Suppress("UnstableApiUsage")
private fun Collection<Long>.p95() =
  Quantiles.percentiles().index(95).compute(this)

/**
 * Measures the given operation applying the given [MetricMeasurement]s.
 */
internal fun <T> Benchmark.measureOperation(measures: List<MetricMeasurement<T>>,
                                            warmUpCount: Int = NUMBER_OF_WARM_UP,
                                            samplesCount: Int = NUMBER_OF_SAMPLES,
                                            printSamples: Boolean = false,
                                            operation: () -> T) {
  assert(measures.map { it.metric.metricName }.distinct().count() == measures.map { it.metric.metricName }.count()) {
    "Metrics can not have duplicate names"
  }
  System.gc()
  repeat(warmUpCount) {
    operation()
  }

  val metricSamples: LinkedListMultimap<String, MetricSample> = LinkedListMultimap.create()
  repeat(samplesCount) {
    measures.forEach { it.before() }
    val result = operation()
    measures.forEach {
      it.after(result)?.let { value -> metricSamples.put(it.metric.metricName, value) }
    }
  }

  measures.forEach { measure ->
    val metric = measure.metric
    val samples = metricSamples.get(metric.metricName)
    if (samples.isNotEmpty()) {
      if (printSamples) {
        val dataPoints = samples.map { it.sampleData }.toList()
        println(
          """
            ${metric.metricName}: ${samples.joinToString(",") { it.sampleData.toString() }}
              median=${dataPoints.median()} p95=${dataPoints.p95()}
          """.trimIndent())
      }
      val analyzers = measure.analyzers
      if (analyzers.isNotEmpty()) {
        metric.setAnalyzers(this, analyzers)
      }
      metric.addSamples(this, *samples.toTypedArray())
      metric.commit()
    }
  }
}


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

fun getRenderMetric(task: RenderTask, resultVerifier: (RenderResult) -> Unit): PerfgateRenderMetric =
  getRenderMetric(task, resultVerifier, resultVerifier)

fun verifyValidatorResult(result: RenderResult) {
  val validatorResult = result.validatorResult
  TestCase.assertTrue(validatorResult is ValidatorHierarchy)
}

fun ImagePool.Image.getPixel(x: Int, y: Int) = this.getCopy(x, y, 1, 1)!!.getRGB(0, 0)