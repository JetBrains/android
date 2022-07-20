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
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import javax.swing.Icon
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener


fun TimeWithPercentage.durationString() = durationString(timeMs)

fun TimeWithPercentage.durationStringHtml() = durationStringHtml(timeMs)

fun TimeWithPercentage.percentageString() = when {
  percentage < 0.1 -> "<0.1%"
  percentage > 99.9 -> ">99.9%"
  else -> "%.1f%%".format(percentage)
}

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
fun htmlTextLabelWithLinesWrap(htmlBodyContent: String, linksHandler: HtmlLinksHandler? = null): JEditorPane =
  SwingHelper.createHtmlViewer(true, null, null, null).apply {
    border = JBUI.Borders.empty()
    isFocusable = true
    SwingHelper.setHtml(this, htmlBodyContent, null)
    if (linksHandler != null) {
      addHyperlinkListener(linksHandler)
    }
    caretPosition = 0
  }

fun htmlTextLabelWithFixedLines(htmlBodyContent: String, linksHandler: HtmlLinksHandler? = null): JEditorPane =
  SwingHelper.createHtmlViewer(false, null, null, null).apply {
    border = JBUI.Borders.empty()
    isFocusable = true
    SwingHelper.setHtml(this, htmlBodyContent, null)
    if (linksHandler != null) {
      addHyperlinkListener(linksHandler)
    }
    caretPosition = 0
  }

fun String.insertBRTags(): String = replace("\n", "<br/>\n")
internal fun helpIcon(text: String): String = "<icon alt='$text' src='AllIcons.General.ContextHelp'>"
internal const val warnIconHtml: String = "<icon alt='Warning' src='AllIcons.General.BalloonWarning'>"

class HtmlLinksHandler(val actionHandlers: ViewActionHandlers) : HyperlinkListener {
  private val registeredLinkActions = mutableMapOf<String, Runnable>()

  fun externalLink(text: String, link: BuildAnalyzerBrowserLinks): String {
    registeredLinkActions[link.name] = Runnable {
      BrowserUtil.browse(link.urlTarget)
      actionHandlers.helpLinkClicked(link)
    }
    return "<a href='${link.name}'>$text</a><icon src='AllIcons.Ide.External_link_arrow'>"
  }

  fun actionLink(text: String, actionId: String, action: Runnable): String {
    registeredLinkActions[actionId] = action
    return "<a href='$actionId'>$text</a>"
  }

  override fun hyperlinkUpdate(hyperlinkEvent: HyperlinkEvent?) {
    if (hyperlinkEvent == null) return
    if (hyperlinkEvent.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      registeredLinkActions[hyperlinkEvent.description]?.run()
    }
  }
}