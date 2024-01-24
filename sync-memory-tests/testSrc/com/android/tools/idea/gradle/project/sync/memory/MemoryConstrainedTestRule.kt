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

import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.cpu.CPU_BENCHMARK
import com.android.tools.idea.gradle.project.sync.gradle.EventRecorder.GC_COLLECTION_TIME_FILE_NAME_SUFFIX
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import com.android.tools.perflogger.Analyzer
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices
import org.junit.rules.ExternalResource
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val GC_COLLECTION_TIME_ANALYZER = listOf(
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

class MemoryConstrainedTestRule(
  private val projectName: String,
  private val maxHeapMB: Int
) : ExternalResource() {
  private val gcCollectionTimeMeasurements = mutableListOf<Pair<Instant, Duration>>()
  private val processedFiles = mutableSetOf<String>()
  private var lastKnownAccumulatedGcCollectionTime = 0.milliseconds
  private var lastKnownDaemonPid : Long? = null

  override fun before() {
    mutateGradleProperties {
      setJvmArgs(jvmArgs.orEmpty().replace("-Xmx60g", "-Xmx${maxHeapMB}m"))
    }
    recordMeasurement("${projectName}_Max_Heap",
                      listOf(Clock.System.now() to (maxHeapMB.toLong() shl 20)))
  }

  val listener = object : GradleSyncListenerWithRoot {
    override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
      // It might be the case where there are no daemons,
      // in which case we just reset the accumulatedd time
      // First item in the list will be the latest started daemon
      val daemonPid = GradleDaemonServices.getDaemonsStatus().firstOrNull()?.pid?.toLong()
      if (gcCollectionTimeMeasurements.isNotEmpty() && (daemonPid == null || daemonPid != lastKnownDaemonPid)) {
        println("!!! Daemon is completely gone or restarted between runs !!! pid: $daemonPid")
        // If a new daemon has started in  between runs, reset the accumulated GC collection time
        lastKnownAccumulatedGcCollectionTime = 0.milliseconds
      }
      lastKnownDaemonPid = daemonPid
      val accumulatedGcCollectionTime = getGcCollectionTime()
      val gcCollectionTime = accumulatedGcCollectionTime - lastKnownAccumulatedGcCollectionTime
      lastKnownAccumulatedGcCollectionTime = accumulatedGcCollectionTime
      gcCollectionTimeMeasurements.add(Clock.System.now() to gcCollectionTime)
      println("GC collection time for last run: $gcCollectionTime")
      println("GC collection time accumulated: $accumulatedGcCollectionTime")
    }
  }


  private fun getGcCollectionTime(): Duration {
    val file = File(OUTPUT_DIRECTORY)
      .walk()
      .first {
        it.name.endsWith(GC_COLLECTION_TIME_FILE_NAME_SUFFIX)
        && !processedFiles.contains(it.name)
      }
    return file.readText().toLong().milliseconds.also {
      processedFiles.add(file.name)
    }
  }

  override fun after() {
    val initialPrefix = "Initial_"
    val droppedPrefix = "Dropped_"
    gcCollectionTimeMeasurements.mapIndexed { index, value ->
      val prefix = when (index) {
        0 -> initialPrefix
        1, 2 -> droppedPrefix
        else -> ""
      }
      "${prefix}GC_Collection_Time" to (value.first to value.second)
    }.groupBy { (type, _,) -> type }
      .mapValues { groupEntry -> groupEntry.value.map {it.second} }.entries // unpack group values
      .forEach { (type, values) ->
        values.forEach { value ->
          println("Recording ${projectName}_$type -> ${value.second.inWholeMilliseconds} ms (${value.second.inWholeSeconds} seconds)")
        }
        recordMeasurement("${projectName}_$type", values.map { it.first to it.second.inWholeMilliseconds}, analyzer = GC_COLLECTION_TIME_ANALYZER)
      }
  }

  private fun recordMeasurement(metricName: String, values: List<Pair<Instant, Long>>, analyzer: List<WindowDeviationAnalyzer>? = null) {
    val benchmarks = listOf(MEMORY_BENCHMARK, CPU_BENCHMARK)
    Metric(metricName).apply {
      benchmarks.forEach { benchmark ->
        values.forEach {
          addSamples(benchmark, Metric.MetricSample(it.first.toEpochMilliseconds(), it.second))
        }
        analyzer?.let {
          setAnalyzers(benchmark, it)
        }
        commit()
      }
    }
  }
}