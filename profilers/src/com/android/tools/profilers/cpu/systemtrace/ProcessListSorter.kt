/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import java.util.Locale

/**
 * Sorts process in such a way that processes more likely to be selected from the user
 * ends up in the beginning of the list.
 */
class ProcessListSorter(nameHint: String) {
  private val nameHintLower = nameHint.toLowerCase(Locale.getDefault())

  fun sort(processList: List<ProcessModel>): List<ProcessModel> {
    return processList.sortedWith(
      // First, if either the left or right names overlap with our hint, we want them first.
      // We do a ByDescending on the first check, because we want the process that return TRUE to
      // be LOWER (so at the beginning).
      compareByDescending { process: ProcessModel -> nameHintLower.contains(process.getLowerName()) }
        // Then if our name starts with < then we have a process whose name did not resolve, so we give them a lower priority.
        .thenBy { process: ProcessModel -> process.getLowerName().startsWith("<") }
        // Then who has more threads.
        .thenBy { process: ProcessModel -> 0 - process.getThreads().size }
        // Then by name.
        .thenBy { process: ProcessModel -> process.getSafeProcessName() }
        // Last tiebreaker is the id.
        .thenBy { process: ProcessModel -> process.id })
  }

  private fun ProcessModel.getLowerName(): String {
    return getSafeProcessName().toLowerCase(Locale.getDefault())
  }
}