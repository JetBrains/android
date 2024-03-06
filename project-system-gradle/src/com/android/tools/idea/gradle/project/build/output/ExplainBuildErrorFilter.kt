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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter.consoleLinkUnderlinedText
import com.android.tools.idea.gradle.project.sync.issues.SyncIssuesReporter.consoleLinkWithSeparatorText
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem

class ExplainBuildErrorFilter : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val linkPrefix = consoleLinkUnderlinedText
    if (!line.contains(linkPrefix)) return null
    val index = line.indexOf(linkPrefix)
    val lineStart = entireLength - line.length
    // Skip " " the URL separator in BuildOutputParserWrapper
    val startIndex = index + consoleLinkWithSeparatorText.length
    // don't apply filters to sync output twice
    if (line.length < startIndex) return null
    val message = line.substring(startIndex)
    // TODO add message
    val linkStart = lineStart + index

    // This should only be called when Studio Bot is available
    val studioBot = StudioBot.getInstance()
    return Filter.Result(
      listOf(ResultItem(linkStart, linkStart + linkPrefix.length) { project ->
        studioBot.chat(project).stageChatQuery("Explain build error: $message", StudioBot.RequestSource.BUILD)
      }))
  }
}
