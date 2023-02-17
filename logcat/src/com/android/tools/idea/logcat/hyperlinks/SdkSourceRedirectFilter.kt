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
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.impl.MultipleFilesHyperlinkInfo
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting

/**
 * A [Filter] that redirects Android SDKSource results from a delegate filter.
 *
 * The filter reads the results of the delegate and replaces results that have a [HyperlinkInfo] for a file with a result that
 * has a [SdkSourceRedirectLinkInfo].
 */
internal class SdkSourceRedirectFilter(
  private val project: Project,
  @VisibleForTesting val delegate: Filter,
) : Filter {
  var apiLevel: Int? = null

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val result = delegate.applyFilter(line, entireLength) ?: return null
    return apiLevel?.let { result.convert(it) } ?: result
  }

  private fun Filter.Result.convert(sdk: Int): Filter.Result = Filter.Result(resultItems.map { it.convert(sdk) })

  @Suppress("UnstableApiUsage") // MultipleFilesHyperlinkInfo is marked as @ApiStatus.Internal
  private fun Filter.ResultItem.convert(sdk: Int): Filter.ResultItem {
    return when (val info = hyperlinkInfo) {
      is MultipleFilesHyperlinkInfo -> Filter.ResultItem(highlightStartOffset, highlightEndOffset, info.convert(sdk))
      is OpenFileHyperlinkInfo -> Filter.ResultItem(highlightStartOffset, highlightEndOffset, info.convert(sdk))
      else -> this
    }
  }

  @Suppress("UnstableApiUsage") // MultipleFilesHyperlinkInfo is marked as @ApiStatus.Internal
  private fun MultipleFilesHyperlinkInfo.convert(sdk: Int): HyperlinkInfo {
    return SdkSourceRedirectLinkInfo(project, filesVariants, descriptor?.line ?: -1, sdk)
  }

  private fun OpenFileHyperlinkInfo.convert(sdk: Int): HyperlinkInfo {
    return virtualFile?.let { SdkSourceRedirectLinkInfo(project, listOf(it), descriptor?.line ?: -1, sdk) } ?: this
  }
}
