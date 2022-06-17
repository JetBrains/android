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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceUrl
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener

/**
 * Base class for all Visual Linting analyzers.
 */
abstract class VisualLintAnalyzer {
  abstract val type: VisualLintErrorType
  abstract val backgroundEnabled: Boolean

  /**
   * Analyze the given [RenderResult] for visual lint issues and return found [VisualLintRenderIssue]s
   */
  fun analyze(renderResult: RenderResult, model: NlModel, tracker: VisualLintUsageTracker,
              runningInBackground: Boolean): List<VisualLintRenderIssue> {
    if (runningInBackground && !backgroundEnabled) {
      return emptyList()
    }
    val issueContents = findIssues(renderResult, model)
    return issueContents.map { createIssue(it, model, tracker) }.toList()
  }

  abstract fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent>

  open fun getHyperlinkListener(): HyperlinkListener? = null

  /** Create [VisualLintRenderIssue] for the given [VisualLintIssueContent]. */
  private fun createIssue(content: VisualLintIssueContent,
                          model: NlModel,
                          tracker: VisualLintUsageTracker): VisualLintRenderIssue {
    val component = componentFromViewInfo(content.view, model)
    tracker.trackIssueCreation(type)
    return VisualLintRenderIssue.builder()
      .summary(content.message)
      .severity(HighlightSeverity.WARNING)
      .model(model)
      .components(if (component == null) mutableListOf() else mutableListOf(component))
      .contentDescriptionProvider(content.descriptionProvider)
      .hyperlinkListener(getHyperlinkListener())
      .type(type)
      .build()
  }

  protected fun previewConfigurations(count: Int): String {
    return if (count == 1) "a preview configuration" else "$count preview configurations"
  }

  protected fun simpleName(view: ViewInfo): String {
    val tagName = (view.cookie as? TagSnapshot)?.tagName ?: view.className
    return tagName.substringAfterLast('.')
  }

  protected fun nameWithId(viewInfo: ViewInfo): String {
    val tagSnapshot = (viewInfo.cookie as? TagSnapshot)
    val name = tagSnapshot?.tagName?.substringAfterLast('.') ?: viewInfo.className
    val id = tagSnapshot?.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)?.let { ResourceUrl.parse(it)?.name }
    return id?.let { "$name (id: $it)" } ?: name
  }

  protected fun componentFromViewInfo(viewInfo: ViewInfo, model: NlModel): NlComponent? {
    val tag = (viewInfo.cookie as? TagSnapshot)?.tag ?: return null
    return model.findViewByTag(tag)
  }

  data class VisualLintIssueContent(val view: ViewInfo, val message: String, val descriptionProvider: (Int) -> HtmlBuilder)
}