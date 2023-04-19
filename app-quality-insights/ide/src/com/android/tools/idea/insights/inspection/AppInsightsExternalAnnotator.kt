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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsModel
import com.android.tools.idea.insights.analysis.StackTraceAnalyzer
import com.android.tools.idea.insights.inspection.AppInsightsExternalAnnotator.AnnotationResult
import com.android.tools.idea.insights.inspection.AppInsightsExternalAnnotator.InitialInfo
import com.android.tools.idea.insights.ui.AppInsightsGutterRenderer
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.android.tools.idea.insights.ui.AppInsightsToolWindowFactory
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import icons.StudioIcons
import javax.swing.Icon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.psi.getLineCount

class AppInsightsExternalAnnotator : ExternalAnnotator<InitialInfo, AnnotationResult>() {
  private val logger = Logger.getInstance(javaClass)
  private val lineMarkerProvider = LineMarkerProvider()
  private val analyzer = StackTraceAnalyzer()

  private data class AnnotationData(val name: String, val lineNumber: Int, val insight: AppInsight)

  @VisibleForTesting
  data class InitialInfo(val insights: Map<String, List<AppInsight>>, val fileLineCount: Int)

  @VisibleForTesting
  data class AnnotationResult(val result: Map<Int, Map<String, List<AppInsight>>>)

  override fun collectInformation(file: PsiFile) = doCollectInformation(file)

  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean) =
    doCollectInformation(file)

  private fun doCollectInformation(file: PsiFile): InitialInfo? {
    if (
      !LineMarkerSettings.getSettings().isEnabled(lineMarkerProvider) ||
        !StudioFlags.APP_INSIGHTS_ENABLED.get() ||
        !StudioFlags.APP_INSIGHTS_GUTTER_SUPPORT.get()
    ) {
      return null
    }

    val groupedInsights =
      AppInsightsTabProvider.EP_NAME.extensionList
        .filter { it.isApplicable() }
        .associate { tabProvider ->
          val configurationManager = tabProvider.getConfigurationManager(file.project)
          val insights =
            when (val model = configurationManager.configuration.value) {
              is AppInsightsModel.Authenticated -> {
                val controller = model.controller

                // Here we do the work just for collecting "matching accuracy" metrics.
                controller.insightsInFile(file, analyzer)

                controller.retrieveLineMatches(file).also {
                  logger.debug(
                    "Found ${it.size} ${tabProvider.tabDisplayName} insights for ${file.name}"
                  )
                }
              }
              is AppInsightsModel.Unauthenticated -> {
                logger.debug(
                  "Skip annotation collection for ${tabProvider.tabDisplayName} because it is unauthenticated."
                )
                emptyList()
              }
              is AppInsightsModel.Uninitialized -> {
                // This should only happen at project startup, when things are initializing.
                // Skip collection until the insights model is authenticated, after which the
                // framework will call to collect again and get the correct annotations.
                logger.debug(
                  "Skip annotation collection for ${tabProvider.tabDisplayName} because it hasn't initialized."
                )
                emptyList()
              }
            }
          tabProvider.tabDisplayName to insights
        }
        .filterValues { it.isNotEmpty() }
    return if (groupedInsights.isEmpty()) null
    else InitialInfo(groupedInsights, file.getLineCount())
  }

  override fun doAnnotate(collectedInfo: InitialInfo?): AnnotationResult {
    val flattenedAnnotationData =
      collectedInfo?.insights?.flatMap { entry ->
        entry.value
          .filter { it.line in 0..collectedInfo.fileLineCount }
          .map { AnnotationData(entry.key, it.line, it) }
      }
        ?: emptyList()

    return AnnotationResult(
      flattenedAnnotationData
        .groupBy { it.lineNumber }
        .mapValues { (_, perLineAnnotations) ->
          perLineAnnotations
            .groupBy { data -> data.name }
            .mapValues { (_, perNameAnnotations) ->
              dedupCrashByLine(perNameAnnotations.map { data -> data.insight })
            }
        }
    )
  }

  override fun apply(file: PsiFile, annotationResult: AnnotationResult?, holder: AnnotationHolder) {
    val doc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return

    annotationResult?.result?.forEach { (line, crashesByTabName) ->
      val startLineOffset = doc.getLineStartOffset(line)
      holder
        .newSilentAnnotation(HighlightSeverity.INFORMATION)
        // We do not care for the line itself, so we use startLineOffset for both params.
        .range(TextRange(startLineOffset, startLineOffset))
        .gutterIconRenderer(
          AppInsightsGutterRenderer(crashesByTabName) { insight, tabName ->
            AppInsightsToolWindowFactory.show(file.project, tabName) { insight.markAsSelected() }
          }
        )
        .create()
    }
  }

  /** Ensures there's only one entry per issue in the crashes list. */
  private fun dedupCrashByLine(crashes: List<AppInsight>): List<AppInsight> =
    crashes.distinctBy { it.issue }

  class LineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = "App quality insights"

    override fun getIcon(): Icon = StudioIcons.Shell.ToolWindows.APP_QUALITY_INSIGHTS

    override fun getLineMarkerInfo(element: PsiElement) = null
  }
}
