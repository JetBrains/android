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
package com.android.tools.idea.diagnostics.hprof.analysis

import com.android.tools.idea.diagnostics.hprof.histogram.Histogram
import com.android.tools.idea.diagnostics.hprof.histogram.HistogramEntry

class ClassNomination(private val histogram: Histogram,
                      private val classLimitPerCategory: Int) {
  fun nominateClasses(): Set<HistogramEntry> {
    val resultClasses = HashSet<HistogramEntry>()
    val entries = histogram.entries
    val histogramByInstances = entries.sortedByDescending { it.totalInstances }
    val interestingClasses = histogramByInstances.filter { isInterestingClass(it.classDefinition.name) }

    histogramByInstances.take(classLimitPerCategory).forEach { resultClasses.add(it) }
    interestingClasses.take(classLimitPerCategory).forEach {
      resultClasses.add(it)
    }

    val histogramByBytes = entries.sortedByDescending { it.totalBytes }
    val interestingClassesByBytes = interestingClasses.sortedByDescending { it.totalBytes }
    histogramByBytes.take(classLimitPerCategory).forEach { resultClasses.add(it) }
    interestingClassesByBytes.take(classLimitPerCategory).forEach { resultClasses.add(it) }
    return resultClasses
  }

  private fun isInterestingClass(name: String): Boolean {
    // Flatten the type of multi-dimensional array
    var name = name
    while (name.startsWith("[[")) {
      name = name.substring(1)
    }

    // Filter out arrays of primitives
    if (name.length == 2 && name[0] == '[') {
      return false
    }

    // Get inner type of object arrays
    if (name.startsWith("[L")) {
      assert(name.last() == ';')
      name = name.substring(2, name.length - 1)
    }

    return !name.startsWith("java.") &&
           !name.startsWith("com.google.common.") &&
           !name.startsWith("kotlin.") &&
           !name.startsWith("com.intellij.util.")
  }
}