/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.histogram

import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.android.tools.idea.diagnostics.hprof.util.TruncatingPrintBuffer
import com.android.tools.idea.diagnostics.hprof.visitors.HistogramVisitor

class Histogram(val entries: List<HistogramEntry>, val instanceCount: Int) {

  fun print(headLimit: Int = Int.MAX_VALUE): String {
    val result = StringBuilder()
    val appendToResult = { s: String -> result.appendln(s); Unit }
    var counter = 0
    var totalInstances = 0L
    var totalBytes = 0L
    TruncatingPrintBuffer(headLimit, 1, appendToResult).use { buffer ->
      entries.forEach { entry ->
        totalBytes += entry.totalBytes
        totalInstances += entry.totalInstances
        buffer.println(formatEntryLine(counter, entry))
        counter++
      }
      buffer.println(
        String.format("Total: %15d %15d %d classes (Total instances: %d)", totalInstances, totalBytes, entries.size, instanceCount))
    }
    result.appendln()
    result.appendln("Top 10 by bytes count:")
    val entriesByBytes = entries.sortedByDescending { it.totalBytes }
    for (i in 0 until 10) {
      result.appendln(formatEntryLine(i + 1, entriesByBytes[i]))
    }
    return result.toString()
  }

  private fun formatEntryLine(counter: Int, entry: HistogramEntry) =
    String.format("%5d: %15d %15d %s", counter + 1, entry.totalInstances, entry.totalBytes, entry.classDefinition.prettyName)

  companion object {
    fun create(parser: HProfEventBasedParser, classStore: ClassStore): Histogram {
      val histogramVisitor = HistogramVisitor(classStore)
      parser.accept(histogramVisitor, "histogram")
      return histogramVisitor.createHistogram()
    }
  }
}
