/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Rect
import android.widget.TextView
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.parsers.TagSnapshot
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintBaseConfigIssues.BaseConfigComponentState
import com.android.utils.HtmlBuilder


/**
 * Analyze the texts in locale configs, and find issues related to locale texts.
 */
fun analyzeLocaleText(renderResult: RenderResult,
                      baseConfigIssues: VisualLintBaseConfigIssues,
                      model: NlModel,
                      output: MutableMap<String, MutableList<Issue>>) {
  val config = renderResult.renderContext?.configuration

  if (isBaseConfig(config)) {
    for (root in renderResult.rootViews) {
      buildMap(root, baseConfigIssues)
    }
  } else {
    for (root in renderResult.rootViews) {
      findLocaleTextIssue(root, baseConfigIssues, model, output)
    }
  }
}

/**
 * Builds map of base config issues.
 * Map contains component as hash, and boolean on the types of issues as value
 */
private fun buildMap(root: ViewInfo, baseConfigIssues: VisualLintBaseConfigIssues) {
  root.children.forEach {
    buildMap(it, baseConfigIssues)
  }

  getKey(root)?.let { key ->
    baseConfigIssues.componentState.getOrPut(key) { BaseConfigComponentState() }.hasI18NEllipsis = isEllipsized(root)
    baseConfigIssues.componentState[key]!!.hasI18NTextTooBig = isTextTooBig(root)
  }
}

/** Returns key based on view info. Key should be consistent between configurations. */
private fun getKey(root: ViewInfo): Int? {
  return (root.cookie as? TagSnapshot)?.tag?.hashCode()
}

/** Returns true if the configuration is the base configuration. */
private fun isBaseConfig(config: Configuration?): Boolean {
  // TODO: Follow up and investigate if there's better way to detect base config.
  return config?.locale?.toString() == "__"
}

/** Find issues related to locale texts */
private fun findLocaleTextIssue(root: ViewInfo,
                                baseConfigIssues: VisualLintBaseConfigIssues,
                                model: NlModel,
                                output: MutableMap<String, MutableList<Issue>>) {
  root.children.forEach {
    findLocaleTextIssue(it, baseConfigIssues, model, output)
  }
  val key = getKey(root) ?: return
  val value = baseConfigIssues.componentState[key]
  val locale = model.configuration.locale.toString()

  if (isEllipsized(root)) {
    createEllipsizedIssue(value, root, model, locale, output)
  } else if (isTextTooBig(root)) {
    createTextTooBigIssue(value, root, model, locale, output)
  }
}

/** Create issues related to text ellipsized */
private fun createEllipsizedIssue(value: BaseConfigComponentState?,
                                  root: ViewInfo,
                                  model: NlModel,
                                  locale: String,
                                  output: MutableMap<String, MutableList<Issue>>) {

  if (value != null && !value.hasI18NEllipsis) {
    // Base locale is not ellipsized but current locale is.
    createIssue(
      root,
      model,
      "The text is ellipsized in locale \"$locale\".",
      htmlBuilder("""The text is ellipsized in locale \"$locale\" but not in default locale.
                  This might not be the intended behaviour. Consider increasing the text view size.""".trimMargin()),
      output)
  }
}

private fun htmlBuilder(msg: String): HtmlBuilder {
  return HtmlBuilder().openHtmlBody().addHtml(msg).closeHtmlBody()
}

/** Create issues related to text being too big (so it doesn't fit in text view) */
private fun createTextTooBigIssue(value: BaseConfigComponentState?,
                                  root: ViewInfo,
                                  model: NlModel,
                                  locale: String,
                                  output: MutableMap<String, MutableList<Issue>>) {
  if (value == null) {
    // We cannot find the base locale information. Create an issue nonetheless to warn users.
    createIssue(
      root,
      model,
      "The text might be cut off.",
      htmlBuilder("The text is too large in locale \"$locale\" to fit inside the TextView."),
      output)
  } else if (!value.hasI18NTextTooBig) {
    // Base locale is not cut off but this locale is - create an issue with appropriate message
    createIssue(
      root,
      model,
      "The text might be cut off.",
      htmlBuilder("""The text is too large in locale \"$locale\" to fit inside the TextView.
           This behavior is different from default locale and might not be intended behavior.""".trimMargin()),
      output)
  }
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
