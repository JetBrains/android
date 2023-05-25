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

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.memory.usage.LightweightHeapTraverse
import com.android.tools.memory.usage.LightweightHeapTraverseConfig
import com.android.tools.memory.usage.LightweightTraverseResult
import com.intellij.openapi.project.Project
import com.intellij.util.io.createDirectories
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.system.measureTimeMillis

class MeasureSyncMemoryUsageRule (
  private val lightweightMode: Boolean,
  disableInitialMeasurements: Boolean = false
) : ExternalResource() {
  private val outputDirectory = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
    it.toPath().createDirectories()
  }.absolutePath
  private val memoryAgentPath = System.getProperty("memory.agent.path")
  private val analysisFlag =
  // This can be specified via --jvmopt="-Dkeep_snapshots=true" in bazel test invocation and  will collect hprofs in the bazel output.
  // It won't do any measurements since it takes extra time, and it's to be used for manual inspection via a profiler.
    if (System.getProperty("keep_snapshots").toBoolean())
      StudioFlags.GRADLE_HPROF_OUTPUT_DIRECTORY
    else
      StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY
  init {
    if (!disableInitialMeasurements) {
      analysisFlag.override(outputDirectory)
    }
  }

  override fun before() {
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.override(lightweightMode)
    mutateGradleProperties {
      setJvmArgs("$jvmArgs -agentpath:${File(memoryAgentPath).absolutePath} -XX:SoftRefLRUPolicyMSPerMB=0")
    }
  }

  override fun after() {
    collectHprofs(outputDirectory)
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.clearOverride()
    analysisFlag.clearOverride()
    File(outputDirectory).delete()
  }


  fun recordMeasurements(projectName: String)  {
    // Free up some memory by closing the Gradle Daemon
    DefaultGradleConnector.close()
    recordIdeMeasurements(projectName)
    recordGradleMeasurements(projectName)
  }

  fun repeatSyncAndMeasure(project: Project, projectName: String, repeatCount: Int) {
    // It's been opened once before and will be opened once more before the final measurement
    // so skipping 2 opens.
    repeat (repeatCount - 2 ) {
      project.requestSyncAndWait()
    }
    // Start measured sync
    analysisFlag.override(outputDirectory)
    project.requestSyncAndWait()

    recordMeasurements("${projectName}_Post_${repeatCount}_Repeats")
  }


  private fun recordIdeMeasurements(projectName: String) {
    // Wait for the IDE to "settle" before taking a measurement. There will be indexing
    // and caching related cleanup jobs still running for a while. This allows them to
    // finish and results in much more reliable values.
    Thread.sleep(Duration.ofSeconds(30).toMillis())

    var result: LightweightTraverseResult?

    val elapsedTimeAfterSync = measureTimeMillis {
      result = LightweightHeapTraverse.collectReport(LightweightHeapTraverseConfig(false, true, true))
    }
    println("Heap traversal for IDE after sync finished in $elapsedTimeAfterSync milliseconds")

    recordMemoryMeasurement("${projectName}_IDE_After_Sync_Total", result!!.totalReachableObjectsSizeBytes)
    recordMemoryMeasurement("${projectName}_IDE_After_Sync", result!!.totalStrongReferencedObjectsSizeBytes)

    println("IDE total size MBs: ${result!!.totalReachableObjectsSizeBytes shr 20} ")
    println("IDE total object count: ${result!!.totalReachableObjectsNumber} ")
    println("IDE strong size MBs: ${result!!.totalStrongReferencedObjectsSizeBytes shr 20} ")
    println("IDE strong object count: ${result!!.totalStrongReferencedObjectsNumber} ")
  }

  private fun recordGradleMeasurements(projectName: String) {
    recordAgentValues(projectName)
    recordHistogramValues(projectName)
  }

  private fun recordAgentValues(projectName: String) {
    for (metricFilePath in File(outputDirectory).walk().filter { !it.isDirectory }.asIterable()) {
      when {
        metricFilePath.name.endsWith("before_sync_strong") -> "Before_Sync"
        metricFilePath.name.endsWith("before_sync_total") -> "Before_Sync_Total"
        metricFilePath.name.endsWith("after_sync_strong") -> "After_Sync"
        metricFilePath.name.endsWith("after_sync_total") -> "After_Sync_Total"
        else -> null
      }?.let { recordMemoryMeasurement("${projectName}_$it", metricFilePath.readText().toLong()) }
    }
  }

  private fun recordHistogramValues(projectName: String) {
    for (metricFilePath in File(outputDirectory).walk().filter { !it.isDirectory }.asIterable()) {
      when {
        metricFilePath.name.endsWith("before_sync_histogram") -> "Before_Sync_Histogram_Experimental"
        metricFilePath.name.endsWith("after_sync_histogram") -> "After_Sync_Histogram_Experimental"
        else -> null
      }?.let {
        val total = metricFilePath.readLines()
          .last()
          .trim()
          .split("\\s+".toRegex())[2]
          .toLong()
        recordMemoryMeasurement("${projectName}_$it", total)
        Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
      }
    }
  }
}

private fun collectHprofs(outputDirectory: String) {
  File(outputDirectory).walk().filter { !it.isDirectory && it.name.endsWith(".hprof") }.forEach {
    Files.move(it.toPath(), TestUtils.getTestOutputDir().resolve(it.name))
  }
}
