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

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.testProjectTemplateFromPath
import com.android.tools.idea.gradle.util.GradleProperties
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.memory.usage.LightweightHeapTraverse
import com.android.tools.memory.usage.LightweightHeapTraverseConfig
import com.android.tools.memory.usage.LightweightTraverseResult
import com.android.tools.perflogger.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectory
import kotlin.system.measureTimeMillis

class MemoryUsageBenchmarkRule (
  private val testEnvironmentRule: IntegrationTestEnvironmentRule,
  private val projectName: String,
  private val memoryLimitMb: Int,
  private val lightweightMode: Boolean
) : ExternalResource() {
  private lateinit var outputDirectory: String
  private val memoryAgentPath = System.getProperty("memory.agent.path")
  private val analysisFlag =
  // This can be specified via --jvmopt="-Dkeep_snapshots=true" in bazel test invocation and  will collect hprofs in the bazel output.
  // It won't do any measurements since it takes extra time, and it's to be used for manual inspection via a profiler.
    if (System.getProperty("keep_snapshots").toBoolean())
      StudioFlags.GRADLE_HPROF_OUTPUT_DIRECTORY
    else
      StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY

  override fun before() {
    outputDirectory = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
      it.toPath().createDirectory()
    }.absolutePath
    analysisFlag.override(outputDirectory)
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.override(lightweightMode)

  }

  override fun after() {
    collectDaemonLogs()
    collectHprofs(outputDirectory)
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.clearOverride()
    analysisFlag.clearOverride()
    File(outputDirectory).delete()
  }


  fun openProjectAndMeasure() = runBlocking {
    startMemoryPolling()
    setJvmArgs()
    testEnvironmentRule.openTestProject(testProjectTemplateFromPath(
      path = MemoryBenchmarkTestSuite.DIRECTORY,
      testDataPath = MemoryBenchmarkTestSuite.TEST_DATA.toString())) {
      // Free up some memory by closing the Gradle Daemon
      DefaultGradleConnector.close()
      recordIdeMeasurements()
      recordGradleMeasurements()
    }
  }

  private fun recordIdeMeasurements() {
    // Wait for the IDE to "settle" before taking a measurement. There will be indexing
    // and caching related cleanup jobs still running for a while. This allows them to
    // finish and results in much more reliable values.
    Thread.sleep(Duration.ofSeconds(30).toMillis())

    var result: LightweightTraverseResult?

    val elapsedTimeAfterSync = measureTimeMillis {
      result = LightweightHeapTraverse.collectReport(LightweightHeapTraverseConfig(false, true, true))
    }
    println("Heap traversal for IDE after sync finished in $elapsedTimeAfterSync milliseconds")

    recordMeasurement("IDE_After_Sync_Total", result!!.totalReachableObjectsSizeBytes)
    recordMeasurement("IDE_After_Sync", result!!.totalStrongReferencedObjectsSizeBytes)

    println("IDE total size MBs: ${result!!.totalReachableObjectsSizeBytes shr 20} ")
    println("IDE total object count: ${result!!.totalReachableObjectsNumber} ")
    println("IDE strong size MBs: ${result!!.totalStrongReferencedObjectsSizeBytes shr 20} ")
    println("IDE strong object count: ${result!!.totalStrongReferencedObjectsNumber} ")
  }

  private fun recordGradleMeasurements() {
    for (metricFilePath in File(outputDirectory).walk().filter { !it.isDirectory }.asIterable()) {
      when {
        metricFilePath.name.endsWith("before_sync_strong") -> "Before_Sync"
        metricFilePath.name.endsWith("before_sync_total") -> "Before_Sync_Total"
        metricFilePath.name.endsWith("after_sync_strong") -> "After_Sync"
        metricFilePath.name.endsWith("after_sync_total") -> "After_Sync_Total"
        else -> null
      }?.let { recordMeasurement(it, metricFilePath.readText().toLong()) }
    }
  }

  private fun setJvmArgs() {
    GradleProperties(MemoryBenchmarkTestSuite.TEST_DATA.resolve(MemoryBenchmarkTestSuite.DIRECTORY).resolve(
      SdkConstants.FN_GRADLE_PROPERTIES).toFile()).apply {
      setJvmArgs(jvmArgs.orEmpty().replace("-Xmx60g", "-Xmx${memoryLimitMb}m"))
      setJvmArgs("$jvmArgs -agentpath:${File(memoryAgentPath).absolutePath}")
      save()
    }
  }

  private fun recordMeasurement(suffix: String, value: Long) {
    val currentTime = Instant.now().toEpochMilli()
    Metric("${projectName}_$suffix").apply {
      addSamples(MemoryBenchmarkTestSuite.BENCHMARK, Metric.MetricSample(currentTime, value))
      commit() // There is only one measurement per type, so we can commit immediately.
    }
  }
}

private fun collectDaemonLogs() {
  val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
  val testOutputDir = TestUtils.getTestOutputDir()
  tmpDir
    .resolve(".gradle/daemon").toFile()
    .walk()
    .filter { it.name.endsWith("out.log") }
    .forEach {
      Files.move(it.toPath(), testOutputDir.resolve(it.name))
    }
}

private fun collectHprofs(outputDirectory: String) {
  File(outputDirectory).walk().filter { !it.isDirectory && it.name.endsWith(".hprof") }.forEach {
    Files.move(it.toPath(), TestUtils.getTestOutputDir().resolve(it.name))
  }
}

private fun startMemoryPolling() {
  // This is used just for logging and diagnosing issues in the test
  CoroutineScope(Dispatchers.IO).launch {
    while (true) {
      File("/proc/meminfo").readLines().filter { it.startsWith("Mem") }.forEach {
        // This will have MemAvailable, MemFree, MemTotal lines
        println("${getTimestamp()} - $it")
      }
      delay(Duration.ofSeconds(15))
    }
  }
}

private fun getTimestamp() = DateTimeFormatter
  .ofPattern("yyyy-MM dd-HH:mm:ss.SSS")
  .withZone(ZoneOffset.UTC)
  .format(Instant.now())
