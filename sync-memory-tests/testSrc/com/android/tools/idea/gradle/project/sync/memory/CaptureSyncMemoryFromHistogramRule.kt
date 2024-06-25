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

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.gradle.CaptureType
import com.android.tools.idea.gradle.project.sync.gradle.MeasurementPluginConfig
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.EDivisiveAnalyzer
import com.android.tools.perflogger.UTestAnalyzer
import com.google.common.truth.Truth
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files

// If `capture_heap` system property is set to `true` the test will also capture the heap alongside
class CaptureSyncMemoryFromHistogramRule(private val projectName: String,
                                         private val disableAnalyzers: Boolean = false,
                                         private val projectToCompareAgainst: String? = null) : ExternalResource() {
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
    val capturedMetricNames = mutableListOf<String>()
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
      val metricToCompareAgainst = projectToCompareAgainst?.let { "${it}_$metricName" }

      recordMemoryMeasurement(capturedMetricNames, "${projectName}_$metricName", TimestampedMeasurement(
        timestamp,
        totalBytes
        ), metricToCompareAgainst)
      Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
    }
    for (metricFilePath in File(OUTPUT_DIRECTORY).walk().filter { !it.isDirectory && it.extension == "hprof" }.asIterable()) {
      Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
    }
    Truth.assertThat(capturedMetricNames).containsExactly(
      "${projectName}_Configuration_Finished",
      "${projectName}_Android_Started",
      "${projectName}_Android_Finished",
      "${projectName}_Sync_Finished",
    )
  }

  private fun recordMemoryMeasurement(
    capturedMetricNames: MutableList<String>,
    metricName: String,
    measurement: TimestampedMeasurement,
    metricToCompareAgainst: String?) {
    Metric(metricName).apply {
      addSamples(MEMORY_BENCHMARK, Metric.MetricSample(
        measurement.first.toEpochMilliseconds(),
        measurement.second
      ))
      if (!disableAnalyzers) {
        val analyzers = mutableListOf<Analyzer>(EDivisiveAnalyzer)
        val runningFromReleaseBranch = System.getProperty("running.from.release.branch").toBoolean()
        val usingUTestAnalyzers = runningFromReleaseBranch || metricToCompareAgainst != null
        if (usingUTestAnalyzers) {
          // U-Test analyzers expect at least 3 points in the data to be available to make a meaningful comparison.
          // For memory benchmarks, we only have one very stable data point, so we can use the same data point 3 times
          // Using a different timestamp, just in case the perfgate somehow groups the data with the same timestamp together.
          // Note: Perfgate also has threshold analyzers that we can use for this purpose, but U-Test is already implemented
          // and I chose not to complicate this further with more different types of analyzers.
          for (i in 1..2) {
            addSamples(MEMORY_BENCHMARK, Metric.MetricSample(measurement.first.toEpochMilliseconds() + i, measurement.second))
          }
        }
        val toleratedChange = 0.05 // 5%
        metricToCompareAgainst?.let { analyzers.add(UTestAnalyzer.forMetricComparison(it, relativeShiftValue = toleratedChange))}
        // When running from a release branch, an additional analyzer to make a comparison
        // between the release branch and the main branch is added.
        if (runningFromReleaseBranch) {
          analyzers.add(UTestAnalyzer.forComparingWithMainBranch(relativeShiftValue = toleratedChange))
        }
        setAnalyzers(MEMORY_BENCHMARK, analyzers)
      }
      commit() // There is only one measurement per type, so we can commit immediately.
    }
    capturedMetricNames.add(metricName)
  }
}