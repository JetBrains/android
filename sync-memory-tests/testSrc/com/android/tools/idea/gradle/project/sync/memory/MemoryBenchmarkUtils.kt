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

import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.util.io.createDirectories
import java.io.File
import java.time.Instant

val MEMORY_BENCHMARK = Benchmark.Builder("Retained heap size")
  .setProject("Android Studio Sync Test")
  .build()

val OUTPUT_DIRECTORY: String = File(System.getenv("TEST_TMPDIR"), "snapshots").also {
  it.toPath().createDirectories()
}.absolutePath

internal fun recordMemoryMeasurement(metricName: String, value: Long) {
  val currentTime = Instant.now().toEpochMilli()
  Metric(metricName).apply {
    addSamples(MEMORY_BENCHMARK, Metric.MetricSample(currentTime, value))
    commit() // There is only one measurement per type, so we can commit immediately.
  }
}