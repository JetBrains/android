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

import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.ui.data.TaskCategoryIssueUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.buildanalyzer.common.TaskCategory
import com.android.buildanalyzer.common.TaskCategoryIssue
import com.android.utils.HtmlBuilder
import com.google.common.annotations.VisibleForTesting
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

fun Int.withPluralization(base: String): String = "$this ${StringUtil.pluralize(base, this)}"

fun TaskCategory.displayName() = toString().split("_").joinToString(separator = " ") { word ->
  word.lowercase().replaceFirstChar { it.uppercase() }
}

fun TaskCategoryIssue.getWarningMessage(nonIncrementalAnnotationProcessors: List<AnnotationProcessorData>): String {
  return when (this) {
    TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED -> """
        Resource IDs will be non-final by default in Android Gradle Plugin 8.0.
        This will break using resource IDs in a static context such as switch statements and annotations.
        To enable this, set android.nonFinalResIds=true in gradle.properties.
      """.trimIndent()
    TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED -> """
        Non-transitive R classes are currently disabled.
        Enable non-transitive R classes for faster incremental compilation.
      """.trimIndent()
    TaskCategoryIssue.RESOURCE_VALIDATION_ENABLED -> """
        Resource validation is currently enabled.
        This validates resources in your project on every debug build.
        To speed up your debug build, set android.disableResourceValidation=true in gradle.properties.
      """.trimIndent()
    TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR -> """
        The following annotation processor(s) are non-incremental, which causes the
        JavaCompile task to always run non-incrementally:

        ${nonIncrementalAnnotationProcessors.joinToString(separator = "\n") { it.className }}

        Consider switching to using an incremental annotation processor.
      """.trimIndent()
    TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD -> """
      Minification is enabled in debug variants.
      Enabling minification has an impact on build time for debug variants. Consider disabling minification for
      faster development flow.
    """.trimIndent()
  }
}
fun TaskCategoryIssue.getLink(): BuildAnalyzerBrowserLinks? {
  return when (this) {
    TaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED -> null
    TaskCategoryIssue.RESOURCE_VALIDATION_ENABLED -> null
    TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD -> null
    TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED -> BuildAnalyzerBrowserLinks.NON_TRANSITIVE_R_CLASS
    TaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR -> BuildAnalyzerBrowserLinks.NON_INCREMENTAL_ANNOTATION_PROCESSORS
  }
}

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

fun HtmlBuilder.createTaskCategoryIssueMessage(taskCategoryIssues: List<TaskCategoryIssueUiData>, linksHandler: HtmlLinksHandler, actionHandlers: ViewActionHandlers) {
  val iconToUse = if (taskCategoryIssues[0].issue.severity == TaskCategoryIssue.Severity.INFO) infoIconHtml else warnIconHtml
  beginTable("VALIGN=TOP")
  taskCategoryIssues.forEach { issueData ->
    var description = issueData.message
    if (issueData.link != null) {
      description += "\n"
      if (issueData.issue == TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED) {
        val migrateRClassLink = linksHandler.actionLink(
          "Click here to migrate your project to use non-transitive R classes",
          "AndroidMigrateToNonTransitiveRClassesAction"
        ) {
          actionHandlers.migrateToNonTransitiveRClass()
        }
        description += "${migrateRClassLink}, or "
      }
      description += linksHandler.externalLink("Learn more", issueData.link)
    }
    description = description.replace("\n", "<BR/>")
    addTableRow(iconToUse, description)
  }
  endTable()
}

fun String.insertBRTags(): String = replace("\n", "<br/>\n")
internal fun helpIcon(text: String): String = "<icon alt='$text' src='AllIcons.General.ContextHelp'>"
internal const val warnIconHtml: String = "<icon alt='Warning' src='AllIcons.General.BalloonWarning'>"
internal const val infoIconHtml: String = "<icon alt='Information' src='AllIcons.General.BalloonInformation'>"

class HtmlLinksHandler(val actionHandlers: ViewActionHandlers) : HyperlinkListener {
  @get:VisibleForTesting
  val registeredLinkActions = mutableMapOf<String, Runnable>()

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