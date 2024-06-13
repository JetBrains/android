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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.android.tools.idea.gradle.project.sync.memory

import com.android.test.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.gradle.CaptureType
import com.android.tools.idea.gradle.project.sync.gradle.MeasurementPluginConfig
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files

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


// If `capture_heap` system property is set to `true` the test will also capture the heap alongside
class CaptureSyncMemoryFromHistogramRule(private val projectName: String) : ExternalResource() {
  override fun before() {
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.override(OUTPUT_DIRECTORY)
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.override(true)

    mutateGradleProperties {
      setJvmArgs("$jvmArgs -XX:SoftRefLRUPolicyMSPerMB=0")
    }
    val captureTypes = setOf(CaptureType.HEAP_HISTOGRAM) +
                       if (System.getProperty("capture_heap").toBoolean()) setOf(CaptureType.HEAP_DUMP) else emptySet()
    MeasurementPluginConfig.configureAndApply(OUTPUT_DIRECTORY, captureTypes)
  }

  override fun after() {
    recordHistogramValues(projectName)
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.clearOverride()
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.clearOverride()
    File(OUTPUT_DIRECTORY).delete()
  }

  private fun recordHistogramValues(projectName: String) {
    for (metricFilePath in File(OUTPUT_DIRECTORY).walk().filter { !it.isDirectory && it.extension == "histogram" }.asIterable()) {
      val lines = metricFilePath.readLines()
      // Files less than three lines are likely not heap snapshots
      if (lines.size < 3) continue
      val totalBytes = lines
        .last()
        .trim()
        .split("\\s+".toRegex())[2]
        .toLong()
      val totalMegabytes = totalBytes shr 20
      val metricName = metricFilePath.toMetricName()
      val timestamp = metricFilePath.toTimestamp()
      println("Recording ${projectName}_$metricName -> $totalBytes bytes ($totalMegabytes MBs)")
      recordMemoryMeasurement("${projectName}_$metricName", TimestampedMeasurement(
        timestamp,
        totalBytes
        ))
        Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
    }
    for (metricFilePath in File(OUTPUT_DIRECTORY).walk().filter { !it.isDirectory && it.extension == "hprof" }.asIterable()) {
      Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
    }
  }

  private fun recordMemoryMeasurement(
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
}