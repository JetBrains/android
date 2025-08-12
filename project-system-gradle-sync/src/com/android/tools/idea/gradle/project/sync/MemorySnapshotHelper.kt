/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.memory.usage.LightweightHeapTraverse
import com.android.tools.memory.usage.LightweightHeapTraverseConfig
import com.android.tools.memory.usage.LightweightTraverseResult
import com.intellij.util.MemoryDumpHelper
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import javax.management.MBeanServer
import javax.management.ObjectName
import kotlin.system.measureTimeMillis

enum class MeasurementCheckpoint {
  ANDROID_STARTED,
  ANDROID_FINISHED,
}

fun captureSnapshot(outputPath: String, checkpoint: MeasurementCheckpoint) {
  val name = checkpoint.name
  try {
    val file = File(outputPath).resolve("${getTimestamp()}-$name.hprof")
    println("Capturing memory snapshot at: ${file.absolutePath}")
    val elapsedTime = measureTimeMillis {
      MemoryDumpHelper.captureMemoryDump(file.absolutePath)
    }
    println("Done in $elapsedTime")
  } catch (e: Exception) {
    println("Error capturing snapshot:  ${e.stackTraceToString()}")
  }
}

fun analyzeCurrentProcessHeap(outputPath: String, checkpoint: MeasurementCheckpoint) {
  val name = checkpoint.name
  println("Starting heap traversal for $name")
  var result: LightweightTraverseResult?
  val elapsedTime = measureTimeMillis {
    result = LightweightHeapTraverse.collectReport(LightweightHeapTraverseConfig(false, true, true))
  }
  println("Heap traversal for $name finished in $elapsedTime milliseconds")

  println("Heap $name total size MBs: ${result!!.totalReachableObjectsSizeBytes shr 20} ")
  println("Heap $name total object count: ${result!!.totalReachableObjectsNumber} ")
  val fileTotal = File(outputPath).resolve("${getTimestamp()}_${name}_total")
  fileTotal.writeText(result!!.totalReachableObjectsSizeBytes.toString())

  println("Heap $name strong size MBs: ${result!!.totalStrongReferencedObjectsSizeBytes shr 20} ")
  println("Heap $name strong object count: ${result!!.totalStrongReferencedObjectsNumber} ")
  val fileStrong = File(outputPath).resolve("${getTimestamp()}_${name}_strong")
  fileStrong.writeText(result!!.totalStrongReferencedObjectsSizeBytes.toString())
}

fun captureHeapHistogramOfCurrentProcess(outputPath: String, checkpoint: MeasurementCheckpoint) {
  val name = checkpoint.name
  println("Recording event $name")
  val server = ManagementFactory.getPlatformMBeanServer()
  val histogram = server.execute("gcClassHistogram").toString()
  val fileHistogram = File(outputPath).resolve("${getTimestamp()}_$name.histogram")
  fileHistogram.writeText(histogram)
}

private fun MBeanServer.execute(name: String) = invoke(
  ObjectName("com.sun.management:type=DiagnosticCommand"),
  name,
  arrayOf(null),
  arrayOf(Array<String>::class.java.name)
)

private fun getTimestamp() = Instant.now().toEpochMilli()