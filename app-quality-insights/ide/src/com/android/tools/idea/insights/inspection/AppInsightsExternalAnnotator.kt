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
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.analysis.StackTraceAnalyzer
import com.android.tools.idea.insights.analytics.AppInsightsPerformanceTracker
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
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import icons.StudioIcons
import javax.swing.Icon
import org.jetbrains.kotlin.idea.base.psi.getLineCount

private val logger = Logger.getInstance(AppInsightsExternalAnnotator::class.java)

class AppInsightsExternalAnnotator : ExternalAnnotator<InitialInfo, AnnotationResult>() {
  private val lineMarkerProvider = LineMarkerProvider()
  private val analyzer = StackTraceAnalyzer()

  data class InitialInfo(
    val insights: List<AppInsight>,
    val vFile: VirtualFile,
    val editor: Editor,
    val project: Project,
  )

  data class AnnotationResult(val insights: List<AppInsight>)

  override fun collectInformation(file: PsiFile): InitialInfo? {
    // We do nothing if there's no editor.
    return null
  }

  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): InitialInfo? {
    if (!LineMarkerSettings.getSettings().isEnabled(lineMarkerProvider)) return null
    val vFile = file.virtualFile ?: return null
    val insights = collectInsights(file, analyzer).takeUnless { it.isEmpty() } ?: return null

    return InitialInfo(insights, vFile, editor, file.project)
  }

  override fun doAnnotate(collectedInfo: InitialInfo?): AnnotationResult? {
    collectedInfo ?: return null

    val project = collectedInfo.project
    val insights = collectedInfo.insights

    if (
      !StudioFlags.APP_INSIGHTS_VCS_SUPPORT.get() ||
        !StudioFlags.APP_INSIGHTS_CHANGE_AWARE_ANNOTATION_SUPPORT.get()
    ) {
      return AnnotationResult(insights)
    }

    val resolved =
      insights.mapNotNull { insight ->
        ProgressManager.checkCanceled()
        if (collectedInfo.editor.isDisposed) return@mapNotNull null
        if (insight.issue.sampleEvent.appVcsInfo !is AppVcsInfo.ValidInfo) return@mapNotNull insight

        insight.updateToCurrentLineNumber(
          collectedInfo.vFile,
          collectedInfo.editor.document,
          project,
        )
      }

    return AnnotationResult(resolved)
  }

  override fun apply(file: PsiFile, annotationResult: AnnotationResult?, holder: AnnotationHolder) {
    annotationResult ?: return

    val project = file.project
    val doc = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
    val validLineNumberRange = 0 until file.getLineCount()
    val insights = annotationResult.insights

    insights
      .groupBy { it.line }
      .filterKeys { it in validLineNumberRange }
      .mapValues { (_, crashes) ->
        // Ensures there's only one entry per issue in the crashes list.
        crashes.distinctBy { it.issue }
      }
      .onEach { (line, crashes) ->
        val startLineOffset = doc.getLineStartOffset(line)
        holder
          .newSilentAnnotation(HighlightSeverity.INFORMATION)
          // We do not care for the line itself, so we use startLineOffset for both params.
          .range(TextRange(startLineOffset, startLineOffset))
          .gutterIconRenderer(
            AppInsightsGutterRenderer(crashes) { insight ->
              AppInsightsToolWindowFactory.show(file.project, insight.provider.displayName) {
                insight.markAsSelected()
              }
            }
          )
          .create()
      }
  }

  /**
   * Returns insights for the given [file] from all kinds of sources (e.g. Crashlytics, Play Vitals,
   * etc).
   *
   * Here each [AppInsightsTabProvider] points to a single kind of source.
   */
  private fun collectInsights(file: PsiFile, analyzer: StackTraceAnalyzer): List<AppInsight> {
    val project = file.project

    return AppInsightsTabProvider.EP_NAME.extensionList
      .filter { it.isApplicable() }
      .map { tabProvider ->
        val configurationManager = tabProvider.getConfigurationManager(project)

        when (val model = configurationManager.configuration.value) {
          is AppInsightsModel.Authenticated -> {
            val controller = model.controller

            // Here we do the work just for collecting "matching accuracy" metrics.
            controller.insightsInFile(file, analyzer)

            controller.insightsInFile(file).also {
              logger.debug("Found ${it.size} ${controller.key} insights for ${file.name}")
            }
          }
          AppInsightsModel.Unauthenticated -> {
            logger.debug(
              "Skip annotation collection for ${tabProvider.displayName} because it is unauthenticated."
            )
            emptyList()
          }
          AppInsightsModel.Uninitialized -> {
            // This should only happen at project startup, when things are initializing.
            // Skip collection until the insights model is authenticated, after which the
            // framework will call to collect again and get the correct annotations.
            logger.debug(
              "Skip annotation collection for ${tabProvider.displayName} because it hasn't initialized."
            )
            emptyList()
          }
          AppInsightsModel.InitialSyncFailed -> {
            // This only happens at project startup and sync failure.
            logger.debug(
              "Skip annotation collection for ${tabProvider.displayName} because it initial sync failed."
            )
            emptyList()
          }
        }
      }
      .flatten()
  }

  /**
   * Returns [AppInsight] with the up-to-date [AppInsight.line] or null if there's no matching line
   * number inferred.
   */
  private fun AppInsight.updateToCurrentLineNumber(
    vFile: VirtualFile,
    document: Document,
    project: Project,
  ): AppInsight? {
    val startTime = System.currentTimeMillis()

    // We try with best attempt.
    val vcsDocument = tryCreateVcsDocumentOrNull(vFile, project) ?: return null

    val oldLineNumber = line
    val newLineNumber = getUpToDateLineNumber(oldLineNumber, vcsDocument, document)

    val endTime = System.currentTimeMillis()
    val latency = endTime - startTime

    service<AppInsightsPerformanceTracker>()
      .recordVersionControlBasedLineNumberMappingLatency(latency)
      .also {
        logger.debug(
          "It takes $latency ms to map line number from $oldLineNumber to $newLineNumber in $vFile."
        )
      }

    newLineNumber ?: return null

    return copy(line = newLineNumber)
  }

  class LineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = "App quality insights"

    override fun getIcon(): Icon = StudioIcons.GutterIcons.APP_QUALITY_INSIGHTS

    override fun getLineMarkerInfo(element: PsiElement) = null
  }
}
