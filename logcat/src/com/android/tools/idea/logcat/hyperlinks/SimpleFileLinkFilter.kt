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
package com.android.tools.idea.logcat.hyperlinks

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.search.PsiShortNamesCache
import kotlin.text.RegexOption.IGNORE_CASE

// This regex is pretty liberal, but we don't allow whitespace in a filename. This is so we don't
// have to wrap the name with some delimiter.
private val fileAndLineRegex =
  "\\b(?<filename>[a-z_][a-z0-9._-]*):(?<line>[0-9]+)\\b".toRegex(IGNORE_CASE)

/**
 * A simple [Filter] that detects project file links
 *
 * A project file link has the syntax `filename:<line-number>` where `filename` is the name of a
 * file owned by the project.
 */
internal class SimpleFileLinkFilter(private val project: Project) : Filter, DumbAware {
  private val hyperlinkInfoFactory = HyperlinkInfoFactory.getInstance()
  private val fileNamesCache = PsiShortNamesCache.getInstance(project)

  override fun applyFilter(line: String, entireLength: Int): Result? {
    val matches = fileAndLineRegex.findAll(line)
    val offset = entireLength - line.length
    val items =
      matches.mapNotNullTo(mutableListOf()) { match ->
        val range = match.range
        val filename = match.groups["filename"]?.value ?: return@mapNotNullTo null
        val lineNumber = match.groups["line"]?.value?.toIntOrNull() ?: return@mapNotNullTo null
        val files = fileNamesCache.getFilesByName(filename).map { it.virtualFile }
        if (files.isEmpty()) {
          return@mapNotNullTo null
        }
        ResultItem(
          offset + range.first,
          offset + range.last + 1,
          hyperlinkInfoFactory.createMultipleFilesHyperlinkInfo(files, lineNumber - 1, project),
        )
      }
    return when {
      items.isEmpty() -> null
      else -> Result(items)
    }
  }
}
