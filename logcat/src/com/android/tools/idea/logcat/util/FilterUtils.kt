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
package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.filters.AndroidLogcatFilterHistory
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.intellij.openapi.project.Project

/**
 * Modifies an existing filter by adding or removing a specified term.
 *
 * If the filter already contains the term, it will remove it, otherwise it will add it.
 *
 * If the resulting filter is invalid, this method will return null.
 */
internal fun toggleFilterTerm(logcatFilterParser: LogcatFilterParser, filter: String, term: String) : String? {
  val newFilter = when {
    filter.contains(term) -> filter.replace("""\b+${term.toRegex(RegexOption.LITERAL)}\b+""".toRegex(), " ").trim()
    filter.isEmpty() -> term
    else -> "$filter $term"
  }

  return if (logcatFilterParser.isValid(newFilter)) newFilter else null
}

internal fun getDefaultFilter(project: Project, androidProjectDetector: AndroidProjectDetector): String {
  val logcatSettings = AndroidLogcatSettings.getInstance()
  val filter = when {
    logcatSettings.mostRecentlyUsedFilterIsDefault -> AndroidLogcatFilterHistory.getInstance().mostRecentlyUsed
    else -> logcatSettings.defaultFilter
  }
  return if (!androidProjectDetector.isAndroidProject(project) && filter.contains("package:mine")) "" else filter
}
