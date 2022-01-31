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

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkListener

/**
 * Base class for all Visual Linting analyzers.
 */
abstract class VisualLintAnalyzer {
  abstract val type: VisualLintErrorType

  /**
   * Analyze the given [RenderResult] for visual lint issues, and collect all such issues in [issueProvider].
   */
  fun analyze(renderResult: RenderResult,
                       model: NlModel,
                       issueProvider: VisualLintIssueProvider,
                       analyticsManager: VisualLintAnalyticsManager) {
    val issueContents = findIssues(renderResult, model)
    issueContents.forEach { createIssue(it, model, issueProvider, analyticsManager) }
  }

  abstract fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent>

  open fun getHyperlinkListener(): HyperlinkListener? = null

  /** Create [VisualLintRenderIssue] and add to [issueProvider]. */
  fun createIssue(content: VisualLintIssueContent,
                  model: NlModel,
                  issueProvider: VisualLintIssueProvider,
                  analyticsManager: VisualLintAnalyticsManager) {
    val component = componentFromViewInfo(content.view, model)
    analyticsManager.trackIssueCreation(type)
    issueProvider.addIssue(
      type,
      VisualLintRenderIssue.builder()
        .summary(content.message)
        .severity(HighlightSeverity.WARNING)
        .model(model)
        .components(if (component == null) mutableListOf() else mutableListOf(component))
        .contentDescriptionProvider(content.descriptionProvider)
        .hyperlinkListener(getHyperlinkListener())
        .type(type)
        .build()
    )
  }

  protected fun previewConfigurations(count: Int): String {
    return if (count == 1) "a preview configuration" else "$count preview configurations"
  }

  protected fun simpleName(view: ViewInfo): String {
    val tagName = (view.cookie as? TagSnapshot)?.tagName ?: view.className
    return tagName.substringAfterLast('.')
  }

  protected fun componentFromViewInfo(viewInfo: ViewInfo, model: NlModel): NlComponent? {
    val tag = (viewInfo.cookie as? TagSnapshot)?.tag ?: return null
    return model.findViewByTag(tag)
  }

  data class VisualLintIssueContent(val view: ViewInfo, val message: String, val descriptionProvider: (Int) -> HtmlBuilder)
}