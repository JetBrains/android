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
package com.android.tools.idea.gradle.project.sync.gradle

import com.sun.management.HotSpotDiagnosticMXBean
import org.gradle.BuildAdapter
import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import java.io.Closeable
import java.io.File
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.time.Instant
import javax.inject.Inject
import javax.management.MBeanServer
import javax.management.ObjectName
import kotlin.time.Duration.Companion.milliseconds

enum class CaptureType {
  HEAP_HISTOGRAM,
  HEAP_DUMP,
  TIMESTAMP
}

enum class MeasurementCheckpoint {
  CONFIGURATION_FINISHED,
  SYNC_FINISHED
}

// Functions and variables can't be top level and has to be static to keep Gradle happy.
object MeasurementPluginConfig {
  var outputPath: String = ""
  var captureTypes: Set<CaptureType> = emptySet()

  /** Call this from a benchmark test to configure and apply the plugin globally via init script in Gradle home. */
  @JvmStatic
  fun configureAndApply(
    outputPath: String,
    captureTypes: Set<CaptureType>
  ) {

    val src = File("tools/adt/idea/sync-memory-tests/testSrc/com/android/tools/idea/gradle/project/sync/gradle/MeasurementPlugin.kt")
    val initScript = File(System.getProperty("gradle.user.home")).resolve("init.gradle.kts")
    src.copyTo(initScript, overwrite = true)
    initScript.appendText("""
      ${this::class.simpleName}.${this::outputPath.name} = "$outputPath"
      ${this::class.simpleName}.${this::captureTypes.name} = setOf(${
      captureTypes.joinToString(",") { "${CaptureType::class.simpleName}.$it" }
    })
      apply<${MeasurementPlugin::class.simpleName}>()
    """.trimIndent())
  }
}

class MeasurementPlugin @Inject constructor(private val registry: BuildEventsListenerRegistry): Plugin<Gradle>, BuildAdapter() {
  override fun apply(gradle: Gradle) {
    gradle.addBuildListener(this)
    registry.onTaskCompletion(gradle.sharedServices.registerIfAbsent("measurement-service", MeasurementService::class.java) {})
  }

  override fun projectsEvaluated(gradle: Gradle) {
    EventRecorder.recordEvent(MeasurementCheckpoint.CONFIGURATION_FINISHED)
  }
}

open class MeasurementServiceParams : BuildServiceParameters
abstract class MeasurementService : OperationCompletionListener, Closeable, BuildService<MeasurementServiceParams> {
  // Ignored event, we only care about the one right before closing
  override fun onFinish(event: FinishEvent?) {}


  override fun close() {
    EventRecorder.recordEvent(MeasurementCheckpoint.SYNC_FINISHED)
    EventRecorder.captureGcCollectionTime()
  }
}

// Functions can't be top level and has to be static to keep Gradle happy.
object EventRecorder {
  const val GC_COLLECTION_TIME_FILE_NAME_SUFFIX = "gcCollectionTime"

  @JvmStatic
  fun recordEvent(checkpoint: MeasurementCheckpoint) {
    println("Recording event ${checkpoint.name}")
    if (CaptureType.HEAP_DUMP in MeasurementPluginConfig.captureTypes) {
      captureHeapOfCurrentProcess(checkpoint)
    }
    if (CaptureType.HEAP_HISTOGRAM in MeasurementPluginConfig.captureTypes) {
      captureHeapHistogramOfCurrentProcess(checkpoint)
    }
    if (CaptureType.TIMESTAMP in MeasurementPluginConfig.captureTypes){
      captureEventTimestamp(checkpoint)
    }
  }

  @JvmStatic
  fun captureGcCollectionTime() {
    val collectionTime = ManagementFactory.getGarbageCollectorMXBeans().sumOf {
      (it as GarbageCollectorMXBean).collectionTime
    }
    val file = File(MeasurementPluginConfig.outputPath).resolve("${Instant.now().toEpochMilli()}_$GC_COLLECTION_TIME_FILE_NAME_SUFFIX")
    file.writeText(collectionTime.toString())
    println("Total accumulated GC Collection time: ${collectionTime.milliseconds}")
  }

  @JvmStatic
  private fun captureEventTimestamp(checkpoint: MeasurementCheckpoint) {
    val name = checkpoint.name
    val now = Instant.now()
    val fileTimestamp = File(MeasurementPluginConfig.outputPath).resolve("${now.toEpochMilli()}_$name.timestamp")
    println("Capturing timestamp at ${fileTimestamp.path}")

    fileTimestamp.writeText(now.toString())
  }

  @JvmStatic
  private fun captureHeapHistogramOfCurrentProcess(checkpoint: MeasurementCheckpoint) {
    val name = checkpoint.name
    val server = ManagementFactory.getPlatformMBeanServer()
    val histogram = server.execute("gcClassHistogram")
    val fileHistogram = File(MeasurementPluginConfig.outputPath).resolve("${Instant.now().toEpochMilli()}_${name}.histogram")
    println("Capturing histogram at ${fileHistogram.path}")
    fileHistogram.writeText(histogram)
  }

  @JvmStatic
  private fun captureHeapOfCurrentProcess(checkpoint: MeasurementCheckpoint) {
    val name = checkpoint.name
    val server: MBeanServer = ManagementFactory.getPlatformMBeanServer()
    val mxBean: HotSpotDiagnosticMXBean =
      ManagementFactory.newPlatformMXBeanProxy(
        server,
        "com.sun.management:type=HotSpotDiagnostic",
        HotSpotDiagnosticMXBean::class.java
      )
    val heapDumpPath = File(MeasurementPluginConfig.outputPath).resolve("${Instant.now().toEpochMilli()}_${name}.hprof").path
    println("Capturing heap dump at $heapDumpPath")
    mxBean.dumpHeap(heapDumpPath, true)
  }

  @JvmStatic
  private fun MBeanServer.execute(name: String) = invoke(
    ObjectName("com.sun.management:type=DiagnosticCommand"),
    name,
    arrayOf(null),
    arrayOf(Array<String>::class.java.name)
  ).toString()
}
