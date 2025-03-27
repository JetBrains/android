/*
 * Copyright (C) 2024 The Android Open Source Project
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
@file:JvmName("IdePerfSummaryTool")

package com.android.tools.idea.ideperftests

import java.nio.file.Path
import java.text.NumberFormat
import java.util.zip.ZipFile
import com.google.gson.Gson

data class Benchmark(val data: Map<String, Long>)
data class SingleMetric(val metric: String, val benchmarks: List<Benchmark>)

/**
 * This is a tool that helps analyze multiple runs of the IDE perf tests after invoking a bazel test with multiple runs.
 *
 * Usage:
 * bazel run <target_name> `bazel info bazel-testlogs` <runs_per_test>
 *
 * Examples:
 *
 * One benchmark:
 *
 * After the following bazel test run:
 * bazel test //tools/adt/idea/ide-perf-tests:intellij.android.ide-perf-tests_tests__SantaTrackerKotlinBenchmark \
 * --config=remote --runs_per_test=10
 *
 * Invoke this tool as follows:
 * bazel run //tools/adt/idea/ide-perf-tests:ide_perf_summary_tool \
 *   -- //tools/adt/idea/ide-perf-tests:intellij.android.ide-perf-tests_tests__SantaTrackerKotlinBenchmark \
 *   `bazel info bazel-testlogs` \
 *   10
 *
 * All benchmarks (note: for the second command, you should probably pipe the output to a text file):
 * bazel test //tools/adt/idea/ide-perf-tests:all --config=remote --runs_per_test=10
 *
 * for l in `bazel query //tools/adt/idea/ide-perf-tests:all`; \
 *   do bazel run //tools/adt/idea/ide-perf-tests:ide_perf_summary_tool \
 *   -- $l `bazel info bazel-testlogs` 10; done
 */
fun main(args: Array<String>) {
  val target = args[0]
  val bazelTestLogDir = args[1]
  val numRuns = Integer.valueOf(args[2])
  val metricNameToDataMap = hashMapOf<String, MutableList<Long>>()

  check(target.startsWith("//"))

  val outputDirectory = Path.of(bazelTestLogDir)
    .resolve(target.removePrefix("//").replace(":", "/"))

  for (i in 1..numRuns) {
    val testOutputZip = outputDirectory.resolve("run_${i}_of_$numRuns/test.outputs/outputs.zip")

    try {
      ZipFile(testOutputZip.toFile()).use { zip ->
        zip.entries().asSequence().filter { it.name.matches(Regex("perf-logs/.*.json")) }.forEach {
          zip.getInputStream(it).use { json ->
            val jsonObject = Gson().fromJson(json.bufferedReader().readText(), SingleMetric::class.java)
            metricNameToDataMap.getOrPut(jsonObject.metric) { mutableListOf() }
              .addAll(jsonObject.benchmarks.flatMap { it.data.values })
          }
        }
      }
    }
    catch (_: Exception) { } // If something went wrong, just skip.
  }

  metricNameToDataMap.entries.sortedBy { it.key }.forEach {
    println("Metric: ${it.key}")
    println("Mean  : ${it.value.average().toLong().pretty()}")
    println("Median: ${it.value.sorted()[it.value.size / 2].pretty()}")
    println("Min   : ${it.value.minOrNull()!!.pretty()}")
    println("Max   : ${it.value.maxOrNull()!!.pretty()}")
    println("-----------------")
  }
}

private fun Long.pretty() = "$this milliseconds"