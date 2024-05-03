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

internal typealias Bytes = Long
internal typealias TimestampedMeasurement = Pair<Instant, Bytes>

internal fun File.toTimestamp() = Instant.fromEpochMilliseconds(name.substringBefore('_').toLong())

internal fun File.toMetricName() = nameWithoutExtension.substringAfter("_").lowercaseEnumName()

private fun String.lowercaseEnumName(): String {
  return this.fold(StringBuilder()) { result, char ->
    result.append(if (result.isEmpty() || result.last() == '_') char.uppercaseChar() else char.lowercaseChar())
  }.toString()
}