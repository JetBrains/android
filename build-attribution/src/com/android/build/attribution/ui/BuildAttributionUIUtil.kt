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
import com.android.build.attribution.ui.data.BuildAnalyzerTaskCategoryIssueUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.ide.common.attribution.BuildAnalyzerTaskCategoryIssue
import com.android.ide.common.attribution.IssueSeverity
import com.android.ide.common.attribution.TaskCategory
import com.android.utils.HtmlBuilder
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

fun TaskCategory.getTaskCategoryDescription() = when (this) {
  TaskCategory.GRADLE -> "Gradle created tasks."
  TaskCategory.JAVA -> "Tasks related to Java source compilation, processing and merging."
  TaskCategory.KOTLIN -> "Tasks related to Kotlin source compilation, processing and merging."
  TaskCategory.ANDROID_RESOURCES -> "Tasks related to Android resources compilation, processing, linking and merging."
  TaskCategory.NATIVE -> "Tasks related to Native build compilation, linking and packaging."
  TaskCategory.JAVA_RESOURCES -> "Tasks related to Java resources merging and packaging."
  TaskCategory.JAVA_DOC -> "Tasks related to Java Doc generation and processing."
  TaskCategory.AIDL -> "Tasks related to AIDL source compilation and processing."
  TaskCategory.RENDERSCRIPT -> "Tasks related to Renderscript sources compilation and processing."
  TaskCategory.SHADER -> "Tasks related to Shader sources compilation and processing."
  TaskCategory.DEXING -> "Tasks related to generating Dex files."
  TaskCategory.ART_PROFILE -> "Tasks related to ART optimization profiles compilation and processing"
  TaskCategory.LINT -> "Tasks related to the Lint tool."
  TaskCategory.MANIFEST -> "Tasks related to Android Manifest merging and compiling."
  TaskCategory.METADATA -> "Tasks related to Metadata generation and processing"
  TaskCategory.TEST -> "Tasks related to test execution."
  TaskCategory.DATA_BINDING -> "Tasks related to data binding."
  TaskCategory.VERIFICATION -> "Tasks that verify the project and dependencies setup."
  TaskCategory.SYNC -> "Tasks related to syncing IDE sources with Gradle."
  TaskCategory.DEPLOYMENT -> "Tasks related to device deployment."
  TaskCategory.HELP -> "Tasks that provide helpful information on the project"
  TaskCategory.APK_PACKAGING -> "Tasks related to packaging APKs."
  TaskCategory.AAR_PACKAGING -> "Tasks related to packaging AARs."
  TaskCategory.BUNDLE_PACKAGING -> "Tasks related to packaging bundles."
  TaskCategory.OPTIMIZATION -> "Tasks related to shrinking sources and resources."
  TaskCategory.MISC -> "General Android Gradle Plugin tasks."
  TaskCategory.UNKNOWN -> "Third-Party plugin tasks."
  TaskCategory.COMPILED_CLASSES -> "Tasks that process the output of compilation tasks."
  // TODO(b/245303698): Don't use else statement as it will be hard to find this when primary category is added.
  else -> ""
}

fun BuildAnalyzerTaskCategoryIssue.getWarningMessage(nonIncrementalAnnotationProcessors: List<AnnotationProcessorData>): String {
  return when (this) {
    BuildAnalyzerTaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED -> """
        Resource IDs will be non-final by default in Android Gradle Plugin 8.0.
        This will break using resource ID's inside switch statements.
        To enable this, set android.nonFinalResIds=true in gradle.properties.
      """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED -> """
        Non-transitive R classes are currently disabled.
        Enable non-transitive R classes for faster incremental compilation.
        To enable this, set android.nonTransitiveRClass=true in gradle.properties.
      """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.RESOURCE_VALIDATION_ENABLED -> """
        Resource validation is currently enabled.
        This validates resources in your project on every debug build.
        To speed up your debug build, set android.disableResourceValidation=true in gradle.properties.
      """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.TEST_SHARDING_DISABLED -> """
        Test sharding between connected devices is currently disabled.
        To use multiple devices to run tests in parallel, set android.androidTest.shardBetweenDevices=true in gradle.properties.
      """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED -> """
        Following the deprecation of RenderScript in the Android platform, we are also removing support for RenderScript
        in the Android Gradle plugin. Starting with Android Gradle plugin 7.2, the RenderScript APIs are deprecated.
        They will continue to function, but will invoke warnings, and will be completely removed in future versions of AGP.
        Click 'Learn more' for more information on how to migrate from RenderScript.
      """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.AVOID_AIDL_UNNECESSARY_USE -> """
        Using AIDL is necessary only if you allow clients from different applications to
        access your service for IPC and want to handle multithreading in your service.
        If you do not need to perform concurrent IPC across different applications, you
        should create your interface by implementing a Binder or, if you want to perform
        IPC, but do not need to handle multithreading, implement your interface using a Messenger.
        """.trimIndent()
    BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR -> """
        The following annotation processor(s) are non-incremental, which causes the
        JavaCompile task to always run non-incrementally:

        ${nonIncrementalAnnotationProcessors.joinToString(separator = "\n") { it.className }}

        Consider switching to using an incremental annotation processor.
      """.trimIndent()
  }
}
fun BuildAnalyzerTaskCategoryIssue.getLink(): BuildAnalyzerBrowserLinks? {
  return when (this) {
    BuildAnalyzerTaskCategoryIssue.NON_FINAL_RES_IDS_DISABLED -> null
    BuildAnalyzerTaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED -> BuildAnalyzerBrowserLinks.NON_TRANSITIVE_R_CLASS
    BuildAnalyzerTaskCategoryIssue.RESOURCE_VALIDATION_ENABLED -> null
    BuildAnalyzerTaskCategoryIssue.TEST_SHARDING_DISABLED -> null
    BuildAnalyzerTaskCategoryIssue.RENDERSCRIPT_API_DEPRECATED -> BuildAnalyzerBrowserLinks.RENDERSCRIPT_MIGRATE
    BuildAnalyzerTaskCategoryIssue.AVOID_AIDL_UNNECESSARY_USE -> BuildAnalyzerBrowserLinks.AIDL_INFO
    BuildAnalyzerTaskCategoryIssue.JAVA_NON_INCREMENTAL_ANNOTATION_PROCESSOR -> BuildAnalyzerBrowserLinks.NON_INCREMENTAL_ANNOTATION_PROCESSORS
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

fun HtmlBuilder.createTaskCategoryIssueMessage(taskCategoryIssues: List<BuildAnalyzerTaskCategoryIssueUiData>, linksHandler: HtmlLinksHandler, actionHandlers: ViewActionHandlers) {
  val iconToUse = if (taskCategoryIssues[0].buildAnalyzerTaskCategoryIssue.severity == IssueSeverity.INFO) infoIconHtml else warnIconHtml
  beginTable("VALIGN=TOP")
  taskCategoryIssues.forEach { issue ->
    var description = issue.message
    if (issue.link != null) {
      description += "\n"
      if (issue.buildAnalyzerTaskCategoryIssue == BuildAnalyzerTaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED) {
        val migrateRClassLink = linksHandler.actionLink("Migrate to non-transitive R classes", "AndroidMigrateToNonTransitiveRClassesAction") {
          actionHandlers.migrateToNonTransitiveRClass()
        }
        description += "${migrateRClassLink}, "
      }
      description += linksHandler.externalLink("Learn more", issue.link)
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