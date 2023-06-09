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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.intellij.util.io.createDirectories
import kotlinx.datetime.Instant
import java.io.File

val MEMORY_BENCHMARK = Benchmark.Builder("Retained heap size")
  .setProject("Android Studio Sync Test")
  .build()

val OUTPUT_DIRECTORY: String = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
  it.deleteRecursively()
  it.toPath().createDirectories()
}.absolutePath

private val ANALYZER = listOf(
  WindowDeviationAnalyzer.Builder()
    // mean, median, min  in this case is all same, since we only have single measurement per type.
    .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
    // This means, out of last 50 runs, only consider 1 "recent", which means the rest is "historic".
    // It's fine to consider only 1 run as recent here because the measurements are quite stable.
    .setRunInfoQueryLimit(50)
    .setRecentWindowSize(1)
    .addMedianTolerance(
      WindowDeviationAnalyzer.MedianToleranceParams.Builder()
        .setConstTerm(0.0)
        .setMadCoeff(0.0)
        .setMedianCoeff(0.05) // flag 5% regressions
        .build())
    .build()
)

internal typealias Bytes = Long
internal typealias TimestampedMeasurement = Pair<Instant, Bytes>

internal fun recordMemoryMeasurement(
  metricName: String,
  measurement: TimestampedMeasurement,
  enableAnalyzer: Boolean = true) {
  Metric(metricName).apply {
    addSamples(MEMORY_BENCHMARK, Metric.MetricSample(
      measurement.first.toEpochMilliseconds(),
      measurement.second
    ))
    if (enableAnalyzer) {
      setAnalyzers(MEMORY_BENCHMARK, ANALYZER)
    }
    commit() // There is only one measurement per type, so we can commit immediately.
  }
}

internal fun File.toTimestamp() = Instant.fromEpochMilliseconds(name.substringBefore('_').toLong())

internal fun File.toMetricName() = name.substringAfter("_").lowercaseEnumName()

private fun String.lowercaseEnumName(): String {
  return this.fold(StringBuilder()) { result, char ->
    result.append(if (result.isEmpty() || result.last() == '_') char.uppercaseChar() else char.lowercaseChar())
  }.toString()
}