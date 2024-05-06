/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput.Companion.BENCHMARK_URL_REGEX
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput.Companion.LINK_GROUP
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * This class manages the parsed output of a benchmark run. The lines array is a list of benchmark lines with the "benchmark: " prefix
 * removed. The constructor expects the testMetrics map, and will look at the {@link BENCHMARK_TEST_METRICS_KEY} key for benchmark specific
 * lines.
 *
 * Each line is processed in the following way,
 * Remove "benchmark: " prefix
 * Collect any potential hyperlinks (expected in markup format, eg [title](url))
 *
 * When formatting the benchmark to be printed to a console, each line is printed with hyperlinks inline formatted according to the
 * benchmark output.
 */

interface HyperlinkListener {
  fun hyperlinkClicked(path: String)
}

class BenchmarkOutput private constructor(val lines: List<BenchmarkLine>) {
  constructor(benchmarkOutput: String) : this(parse(benchmarkOutput))

  /**
   * Merge the contents of two BenchmarkOutput objects into a new unified benchmark object.
   */
  fun fold(other: BenchmarkOutput) = BenchmarkOutput(lines + other.lines)

  fun print(console: ConsoleView, type: ConsoleViewContentType, hyperlinkListener: HyperlinkListener? = null) {
    lines.forEach {
      it.print(console, type, hyperlinkListener)
    }
  }

  companion object {
    val Empty = BenchmarkOutput("")
    // Valid string: Benchmark test took [200 ms](path/to/my.trace) to run.
    // text = 200 ms, url = path/to/my.trace, title = "", total = "[200 ms](path/to/my.trace)"
    val BENCHMARK_URL_REGEX = Regex("(?<total>\\[(?<text>.+?)\\]\\((?<link>[^ ]+?)(?: \"(?<title>.+?)\")?\\))")
    val LINK_GROUP = "link"
    val BENCHMARK_TRACE_FILE_PREFIX = "file://"
  }
}

/**
 * BenchmarkLine is a helper class to collect and print hyperlinks to a given {@link ConsoleView}
 */
class BenchmarkLine(val rawText: String, val matches: MatchResult?) {
  fun print(console: ConsoleView, type: ConsoleViewContentType, hyperlinkListener: HyperlinkListener?) {
    if (matches == null) {
      console.print(rawText, type)
    } else {
      var match = matches
      var offsetStart = 0
      while (match != null) {
        val nextStart = match.range.start
        val title = match.groups["title"]?.value ?: match.groups["text"]?.value ?: match.groups["url"]?.value ?: "[Link]"
        val link = match.groups[LINK_GROUP]?.value ?: ""
        console.print(rawText.substring(offsetStart, nextStart), type)
        console.printHyperlink(title) {
          hyperlinkListener?.hyperlinkClicked(link)
        }
        offsetStart = match.range.endInclusive+1
        match = match.next()
      }
      console.print(rawText.substring(offsetStart), type)
    }
    console.print("\n", type)
  }
}

/**
 * Retrieves benchmark output text from a given [testMetrics].
 */
private fun parse(benchmarkOutput: String): List<BenchmarkLine> {
  if (benchmarkOutput.isEmpty()) {
    return mutableListOf()
  }
  val strLines = benchmarkOutput.split("\n")
  return strLines.map {
    BenchmarkLine(it, BENCHMARK_URL_REGEX.find(it))
  }.toList()
}