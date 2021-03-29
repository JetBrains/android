/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.TimeWithPercentage
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane


fun TimeWithPercentage.durationString() = durationString(timeMs)

fun TimeWithPercentage.durationStringHtml() = durationStringHtml(timeMs)

fun TimeWithPercentage.percentageString() = if (percentage >= 0.1) "%.1f%%".format(percentage) else "<0.1%"

fun TimeWithPercentage.percentageStringHtml() = StringUtil.escapeXmlEntities(percentageString())

fun durationString(timeMs: Long) = when {
  timeMs == 0L -> "0.0s"
  timeMs < 100L -> "<0.1s"
  else -> "%.1fs".format(timeMs.toDouble() / 1000)
}

fun durationStringHtml(timeMs: Long) = StringUtil.escapeXmlEntities(durationString(timeMs))

fun warningsCountString(warningsCount: Int) = when (warningsCount) {
  0 -> ""
  1 -> "1 warning"
  else -> "${warningsCount} warnings"
}

fun warningIcon(): Icon = AllIcons.General.BalloonWarning

/**
 * Label with auto-wrapping turned on that accepts html text.
 * Used in Build Analyzer to render long multi-line text.
 */
fun htmlTextLabelWithLinesWrap(htmlBodyContent: String): JEditorPane =
  SwingHelper.createHtmlViewer(true, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, null)
  }

fun htmlTextLabelWithFixedLines(htmlBodyContent: String): JEditorPane =
  SwingHelper.createHtmlViewer(false, null, null, null).apply {
    border = JBUI.Borders.empty()
    SwingHelper.setHtml(this, htmlBodyContent, null)
  }

/**
 * Wraps long path to spans to make it possible to auto-wrap to a new line
 */
fun wrapPathToSpans(text: String): String = "<p>${text.replace("/", "<span>/</span>")}</p>"
fun String.insertBRTags(): String = replace("\n", "<br/>\n")
fun externalLink(text: String, link: BuildAnalyzerBrowserLinks) =
  "<a href='${link.name}'>$text</a><icon src='ide/external_link_arrow.svg'>"