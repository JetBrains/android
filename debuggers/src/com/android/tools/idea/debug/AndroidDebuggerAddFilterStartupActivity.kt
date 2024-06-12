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

package com.android.tools.idea.debug

import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.classFilter.ClassFilter

private val FILTERS = listOf(
  "android.*",
  "com.android.*",
  "androidx.*",
  "libcore.*",
  "dalvik.*",
)

/**
 * Adds Android specific stepping filters
 *
 * Based on [org.jetbrains.kotlin.idea.debugger.core.filter.JvmDebuggerAddFilterStartupActivity]
 */
private class AndroidDebuggerAddFilterStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val settings = serviceAsync<DebuggerSettings>()
    FILTERS.forEach {
      settings.addSteppingFilterIfNeeded(it)
    }
  }
}

private fun DebuggerSettings.addSteppingFilterIfNeeded(pattern: String) {
  when (val occurrencesNum = steppingFilters.count { it.pattern == pattern }) {
    0 -> steppingFilters += ClassFilter(pattern)
    1 -> return
    else -> leaveOnlyFirstOccurrenceOfSteppingFilter(pattern, occurrencesNum)
  }
}

private fun DebuggerSettings.leaveOnlyFirstOccurrenceOfSteppingFilter(pattern: String, occurrencesNum: Int) {
  val newFilters = ArrayList<ClassFilter>(steppingFilters.size - occurrencesNum + 1)

  var firstOccurrenceFound = false
  for (filter in steppingFilters) {
    if (filter.pattern == pattern) {
      if (!firstOccurrenceFound) {
        newFilters.add(filter)
        firstOccurrenceFound = true
      }
    }
    else {
      newFilters.add(filter)
    }
  }

  steppingFilters = newFilters.toTypedArray()
}
