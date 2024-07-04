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

import android.view.accessibility.AccessibilityNodeInfo
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceUrl
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentBackendEmpty
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.parsers.PsiXmlTag
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintOrigin
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.parsers.TagSnapshot
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/** Base class for all Visual Linting analyzers. */
abstract class VisualLintAnalyzer {
  abstract val type: VisualLintErrorType
  abstract val backgroundEnabled: Boolean

  /**
   * Analyze the given [RenderResult] for visual lint issues and return found
   * [VisualLintRenderIssue]s
   */
  fun analyze(
    renderResult: RenderResult,
    model: NlModel,
    severity: HighlightSeverity,
    runningInBackground: Boolean,
  ): List<VisualLintRenderIssue> {
    if (runningInBackground && !backgroundEnabled) {
      return emptyList()
    }
    val issueContents = findIssues(renderResult, model)
    return issueContents.map { createIssue(it, model, severity) }.toList()
  }

  abstract fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent>

  private fun getHyperlinkListener(
    issueOrigin: VisualLintOrigin,
    type: VisualLintErrorType,
  ): HyperlinkListener {
    val listener = createDefaultHyperLinkListener()
    return HyperlinkListener {
      listener.hyperlinkUpdate(it)
      if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        VisualLintUsageTracker.getInstance().trackClickHyperLink(type, issueOrigin)
      }
    }
  }

  /** Create [VisualLintRenderIssue] for the given [VisualLintIssueContent]. */
  private fun createIssue(
    content: VisualLintIssueContent,
    model: NlModel,
    severity: HighlightSeverity,
  ): VisualLintRenderIssue {
    val component = componentFromViewInfo(content.view, model)
    val issueOrigin =
      if (component?.backend is NlComponentBackendEmpty) VisualLintOrigin.UI_CHECK
      else VisualLintOrigin.XML_LINTING
    val issueType = content.overriddenErrorType ?: type
    VisualLintUsageTracker.getInstance().trackIssueCreation(issueType, issueOrigin, model.facet)
    return VisualLintRenderIssue.builder()
      .summary(content.message)
      .severity(severity)
      .model(model)
      .components(if (component == null) mutableListOf() else mutableListOf(component))
      .contentDescriptionProvider(content.descriptionProvider)
      .hyperlinkListener(getHyperlinkListener(issueOrigin, issueType))
      .type(issueType)
      .build()
  }

  protected fun previewConfigurations(count: Int): String {
    return if (count == 1) "a preview configuration" else "$count preview configurations"
  }

  protected fun simpleName(view: ViewInfo): String {
    if (view.cookie is TagSnapshot) {
      return (view.cookie as TagSnapshot).tagName.substringAfterLast('.')
    } else if (
      view.accessibilityObject is AccessibilityNodeInfo && view.className == "android.view.View"
    ) {
      return "Composable"
    }
    return view.className.substringAfterLast('.')
  }

  protected fun nameWithId(viewInfo: ViewInfo): String {
    val tagSnapshot = (viewInfo.cookie as? TagSnapshot)
    val name = simpleName(viewInfo)
    val id =
      tagSnapshot?.getAttribute(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)?.let {
        ResourceUrl.parse(it)?.name
      }
    return id?.let { "$id <$name>" } ?: name
  }

  protected fun componentFromViewInfo(viewInfo: ViewInfo?, model: NlModel): NlComponent? {
    val accessibilityNodeInfo = viewInfo?.accessibilityObject
    if (accessibilityNodeInfo is AccessibilityNodeInfo) {
      return model.treeReader.findViewByAccessibilityId(accessibilityNodeInfo.sourceNodeId)
    }
    val tag =
      (viewInfo?.cookie as? TagSnapshot)?.tag as? PsiXmlTag
        ?: return model.treeReader.components.firstOrNull()
    return model.treeReader.findViewByTag(tag.psiXmlTag)
  }

  protected fun checkIsClass(viewInfo: ViewInfo, clazz: Class<*>): Boolean {
    return clazz.isInstance(viewInfo.viewObject) || clazz.canonicalName == viewInfo.className
  }

  data class VisualLintIssueContent(
    val view: ViewInfo?,
    val message: String,
    // Overrides the error type, it is used if we need to specify a type for the error
    // that is different from the default type provided by the analyzer that created the error.
    val overriddenErrorType: VisualLintErrorType? = null,
    val descriptionProvider: (Int) -> HtmlBuilder,
  )
}
