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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import android.graphics.Rect
import android.widget.TextView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderConfiguration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintBaseConfigIssues
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintInspection
import com.android.utils.HtmlBuilder

/**
 * [VisualLintAnalyzer] for issues with texts in different locales.
 */
class LocaleAnalyzer(private val baseConfigIssues: VisualLintBaseConfigIssues) : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.LOCALE_TEXT

  override val backgroundEnabled: Boolean
    get() = LocaleAnalyzerInspection.localeBackground

  override fun findIssues(renderResult: RenderResult, model: NlModel): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val config = renderResult.renderContext?.configuration

    if (isBaseConfig(config)) {
      val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
      while (viewsToAnalyze.isNotEmpty()) {
        val view = viewsToAnalyze.removeLast()
        view.children.forEach { viewsToAnalyze.addLast(it) }
        buildMap(view, baseConfigIssues)
      }
    } else {
      val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
      while (viewsToAnalyze.isNotEmpty()) {
        val view = viewsToAnalyze.removeLast()
        view.children.forEach { viewsToAnalyze.addLast(it) }
        issues.addAll(findLocaleIssues(view, baseConfigIssues, model))
      }
    }
    return issues
  }

  /**
   * Builds map of base config issues.
   * Map contains component as hash, and boolean on the types of issues as value
   */
  private fun buildMap(view: ViewInfo, baseConfigIssues: VisualLintBaseConfigIssues) {
    getKey(view)?.let { key ->
      baseConfigIssues.componentState.getOrPut(key) { VisualLintBaseConfigIssues.BaseConfigComponentState() }.hasI18NEllipsis = isEllipsized(view)
      baseConfigIssues.componentState[key]!!.hasI18NTextTooBig = isTextTooBig(view)
    }
  }

  /** Returns key based on view info. Key should be consistent between configurations. */
  private fun getKey(root: ViewInfo): Int? {
    return (root.cookie as? TagSnapshot)?.tag?.hashCode()
  }

  /** Returns true if the configuration is the base configuration. */
  private fun isBaseConfig(config: RenderConfiguration?): Boolean {
    // TODO: Follow up and investigate if there's better way to detect base config.
    return config?.locale?.toString() == "__"
  }

  /** Find issues related to locale texts */
  private fun findLocaleIssues(view: ViewInfo,
                               baseConfigIssues: VisualLintBaseConfigIssues,
                               model: NlModel): List<VisualLintIssueContent> {
    val issues = mutableListOf<VisualLintIssueContent>()
    val key = getKey(view) ?: return issues
    val value = baseConfigIssues.componentState[key]
    val locale = model.configuration.locale.toString()

    if (isEllipsized(view) && value != null && !value.hasI18NEllipsis) {
      issues.add(createEllipsizedIssue(view, locale))
    } else if (isTextTooBig(view) && (value == null || !value.hasI18NTextTooBig)) {
      issues.add(createTextTooBigIssue(value, view, locale))
    }
    return issues
  }

  /** Create issues related to text ellipsized */
  private fun createEllipsizedIssue(view: ViewInfo, locale: String): VisualLintIssueContent {
    val summary = "The text is ellipsized in locale \"$locale\"."
    val content = HtmlBuilder().add("The text is ellipsized in locale \"$locale\" but not in default locale.")
      .newline()
      .add("This might not be the intended behaviour. Consider increasing the text view size.")
    return VisualLintIssueContent(view, summary) { content }
  }

  /** Create issues related to text being too big (so it doesn't fit in text view) */
  private fun createTextTooBigIssue(value: VisualLintBaseConfigIssues.BaseConfigComponentState?, view: ViewInfo, locale: String): VisualLintIssueContent {
    val summary = "The text might be cut off."
    val content = if (value == null) {
      HtmlBuilder().add("The text is too large in locale \"$locale\" to fit inside the TextView.")
    }
    else {
      // Base locale is not cut off but this locale is - create an issue with appropriate message
      HtmlBuilder().add("The text is too large in locale \"$locale\" to fit inside the TextView.")
        .newline()
        .add("This behavior is different from default locale and might not be intended behavior.")
    }
    return VisualLintIssueContent(view, summary) { content }
    // TODO: As a follow up, create a render lint that captures base locale text being cut off.
  }

  /** Returns true if the view is a text view and is ellipsized. */
  private fun isEllipsized(view: ViewInfo): Boolean {
    if (view.viewObject !is TextView) {
      return false
    }

    val textView = view.viewObject as TextView
    val layout = textView.layout
    val lines = layout.lineCount
    return ((lines - 1) downTo 0).any { layout.getEllipsisCount(it) > 0 }
  }

  /** Returns true if text is larger than the view bounds */
  private fun isTextTooBig(view: ViewInfo): Boolean {
    if (view.viewObject !is TextView) {
      return false
    }

    val textView = view.viewObject as TextView
    val paint = textView.paint
    val text = textView.text

    val lineHeight = textView.lineHeight
    val lineCount = textView.lineCount
    val requiredTextBoundsHeight = lineHeight * lineCount
    if (requiredTextBoundsHeight > textView.height) {
      return true
    }

    var requiredTextBoundsWidth = 0
    for (line in 0 until lineCount) {
      val start = textView.layout.getLineStart(line)
      val end = textView.layout.getLineEnd(line)
      val rect = Rect()
      paint.getTextBounds(text, start, end, rect)

      requiredTextBoundsWidth = requiredTextBoundsWidth.coerceAtLeast(rect.width())
    }

    return requiredTextBoundsWidth > textView.width
  }

}

object LocaleAnalyzerInspection: VisualLintInspection(VisualLintErrorType.LOCALE_TEXT, "localeBackground") {
  var localeBackground = true
}
