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
package com.android.tools.idea.gradle.project.sync.cpu

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.gradle.CaptureType
import com.android.tools.idea.gradle.project.sync.gradle.MeasurementCheckpoint
import com.android.tools.idea.gradle.project.sync.gradle.MeasurementPluginConfig
import com.android.tools.idea.gradle.project.sync.memory.OUTPUT_DIRECTORY
import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.intellij.openapi.project.Project
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.annotations.SystemIndependent
import org.junit.rules.ExternalResource
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit
import com.android.tools.idea.gradle.project.sync.MeasurementCheckpoint as AndroidMeasurementCheckpoint


val CPU_BENCHMARK = Benchmark.Builder("Cpu time")
  .setProject("Android Studio Sync Test")
  .build()

private val ANALYZER = listOf(
  WindowDeviationAnalyzer.Builder()
    .setMetricAggregate(Analyzer.MetricAggregate.MEDIAN)
    // This means, out of last 100 runs, only consider the last 50 "recent", including the current one.
    // The rest is "historic". The analyzer works by compares the set of recent values and historic values
    .setRunInfoQueryLimit(100)
    .setRecentWindowSize(50)
    .addMedianTolerance(
      WindowDeviationAnalyzer.MedianToleranceParams.Builder()
        .setConstTerm(0.0)
        .setMadCoeff(0.0)
        .setMedianCoeff(0.05) // flag 5% regressions
        .build())
    .build()
)

private typealias TimestampedMeasurement = Pair<Instant, Duration>

private data class Durations(
  val gradleConfiguration : Duration,
  val gradleBeforeAndroidExecution: Duration,
  val gradleAndroidExecution: Duration,
  val gradleAfterAndroidExecution: Duration,
  val ide: Duration,
  val finishTimestamp: Instant,
  val gradle: Duration = gradleConfiguration + gradleBeforeAndroidExecution + gradleAndroidExecution + gradleAfterAndroidExecution,
  val total: Duration = gradle + ide
) {
  override fun toString() = """
total: ${total.inWholeSeconds}s
  -   ide: ${ide.inWholeSeconds}s
  -gradle: ${gradle.inWholeSeconds}s
    -configuration: ${gradleConfiguration.inWholeSeconds}s
    -beforeAndroid: ${gradleBeforeAndroidExecution.inWholeSeconds}s
    -      android: ${gradleAndroidExecution.inWholeSeconds}s
    - afterAndroid: ${gradleAfterAndroidExecution.inWholeSeconds}s
  """.trimIndent()
}

class MeasureSyncExecutionTimeRule(val syncCount: Int) : ExternalResource() {
  private val results = mutableListOf<Durations>()
  private val processedFiles = mutableSetOf<String>()
  private lateinit var syncStartTimestamp : Instant

  override fun before() {
    StudioFlags.SYNC_STATS_OUTPUT_DIRECTORY.override(OUTPUT_DIRECTORY)
    MeasurementPluginConfig.configureAndApply(OUTPUT_DIRECTORY, captureTypes = setOf(CaptureType.TIMESTAMP))
  }

  val listener = object : GradleSyncListenerWithRoot {
    override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
      syncStartTimestamp = Clock.System.now()
    }

    override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
      val configurationFinishedTimestamp = getTimestampForCheckpoint(MeasurementCheckpoint.CONFIGURATION_FINISHED.name)
      val androidStartedTimestamp = getTimestampForCheckpoint(AndroidMeasurementCheckpoint.ANDROID_STARTED.name)
      val androidFinishedTimestamp = getTimestampForCheckpoint(AndroidMeasurementCheckpoint.ANDROID_FINISHED.name)
      val gradleSyncFinishedTimestamp = getTimestampForCheckpoint(MeasurementCheckpoint.SYNC_FINISHED.name)
      val ideFinishedTimestamp = Clock.System.now()

      val result = Durations(
        gradleConfiguration = configurationFinishedTimestamp - syncStartTimestamp,
        gradleBeforeAndroidExecution = androidStartedTimestamp - configurationFinishedTimestamp,
        gradleAndroidExecution = androidFinishedTimestamp - androidStartedTimestamp,
        gradleAfterAndroidExecution = gradleSyncFinishedTimestamp - androidFinishedTimestamp,
        ide = ideFinishedTimestamp - gradleSyncFinishedTimestamp,
        finishTimestamp =  ideFinishedTimestamp
      )
      results.add(result)
      println("Project import #${results.size} result: $result")
    }
  }

  fun recordMeasurements(projectName: String) {
    val initialPrefix = "Initial_"
    val droppedPrefix = "Dropped_"
    results.flatMapIndexed { index, value ->
      val prefix = when (index) {
        0 -> initialPrefix
        1, 2 -> droppedPrefix
        else -> ""
      }
      listOf(
        "${prefix}Gradle_Configuration_Ms" to TimestampedMeasurement(value.finishTimestamp, value.gradleConfiguration),
        "${prefix}Gradle_Before_Android_Execution_Ms" to TimestampedMeasurement(value.finishTimestamp, value.gradleBeforeAndroidExecution),
        "${prefix}Gradle_Android_Execution_Ms" to TimestampedMeasurement(value.finishTimestamp, value.gradleAndroidExecution),
        "${prefix}Gradle_After_Android_Execution_Ms" to TimestampedMeasurement(value.finishTimestamp, value.gradleAfterAndroidExecution),
        "${prefix}Gradle_Ms" to TimestampedMeasurement(value.finishTimestamp, value.gradle),
        "${prefix}Ide_Ms" to TimestampedMeasurement(value.finishTimestamp, value.ide),
        "${prefix}Total_Ms" to TimestampedMeasurement(value.finishTimestamp, value.total),
      )
      }.groupBy { (type, _,) -> type }
      .mapValues { groupEntry -> groupEntry.value.map {it.second} }.entries // unpack group values
      .forEach { (type, values: List<TimestampedMeasurement>) ->
      values.forEach { value ->
        println("Recording ${projectName}_$type -> ${value.second.inWholeMilliseconds} ms (${value.second.inWholeSeconds} seconds)")
      }
      recordCpuMeasurement("${projectName}_$type", values, enableAnalyzers = !type.startsWith(droppedPrefix) )
    }
  }
  private fun getTimestampForCheckpoint(checkpointName: String): Instant {
    val file = File(OUTPUT_DIRECTORY).walk().first { it.nameWithoutExtension.endsWith(checkpointName) && !processedFiles.contains(it.name)}
    return Instant.fromEpochMilliseconds(file.name.substringBefore('_').toLong()).also {
      processedFiles.add(file.name)
    }
  }
}

internal fun recordCpuMeasurement(metricName: String, values: Iterable<TimestampedMeasurement>, enableAnalyzers: Boolean) {
  Metric(metricName).apply {
    values.forEach {
      addSamples(CPU_BENCHMARK, Metric.MetricSample(it.first.toEpochMilliseconds(), it.second.toLong(DurationUnit.MILLISECONDS)))
    }
    if (enableAnalyzers) {
      setAnalyzers(CPU_BENCHMARK, ANALYZER)
    }
    commit()
  }
}