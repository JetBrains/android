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
package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.vcs.getVcsManager
import com.android.tools.idea.insights.vcs.locateRepository
import com.android.tools.idea.insights.vcs.toVcsFilePath
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.ExceptionWorker.parseExceptionLine
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.InlayProvider
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.net.URL
import javax.swing.JComponent
import kotlin.math.ceil

/**
 * Custom filter for attaching inlay diff links for traces if applicable.
 *
 * A diff (historical source file from the affected commit VS current source file) link is attached
 * just after the file line number when
 * 1) the class/file extracted from a trace line is navigable/resolvable and within project scope,
 * 2) an app VCS info is found (this piece of info is captured when an app is built and is sent
 *    along with a crash)
 */
class InsightsAttachInlayDiffLinkFilter(
  private val exceptionInfoCache: InsightsExceptionInfoCache,
  private val containingConsole: ConsoleViewImpl,
  private val tracker: AppInsightsTracker,
) : Filter {
  private val project = containingConsole.project

  private fun fetchVcsInfo(): AppVcsInfo.ValidInfo? {
    return containingConsole.getClientProperty(VCS_INFO_OF_SELECTED_CRASH) as? AppVcsInfo.ValidInfo
  }

  private fun fetchAssociatedConnection(): Connection? {
    return containingConsole.getClientProperty(CONNECTION_OF_SELECTED_CRASH) as? Connection
  }

  private fun createContextDataForDiff(
    appVcsInfo: AppVcsInfo.ValidInfo,
    virtualFiles: List<VirtualFile>,
    lineNumber: Int,
  ): ContextDataForDiff? {
    // For now, we just pick the first matching vcs info as AGP doesn't support multi-repo case yet.
    val firstVcsInfo =
      appVcsInfo.repoInfo.firstOrNull { it.locateRepository(project) != null } ?: return null

    return virtualFiles
      .mapNotNull { vFile ->
        // Check if this virtual file is under VCS or not.
        vFile.getVcsManager(project) ?: return@mapNotNull null

        ContextDataForDiff(
          vcsKey = firstVcsInfo.vcsKey,
          revision = firstVcsInfo.revision,
          filePath = vFile.toVcsFilePath(),
          lineNumber = lineNumber,
          origin = fetchAssociatedConnection(),
        )
      }
      .firstOrNull()
  }

  override fun applyFilter(line: String, textEndOffset: Int): Filter.Result? {
    val foundVcsInfo = fetchVcsInfo() ?: return null

    // TODO: Here it's only for normal stack trace line parsing;
    //  for Kotlin native one, maybe follow KotlinExceptionFilterFactory#parseNativeStackTraceLine.
    val parsedLineInfo = parseExceptionLine(line) ?: return null

    val lineNumber =
      parsedLineInfo.lineNumber.takeUnless { it < 1 } ?: return null // It's 1-based line number.

    val className = parsedLineInfo.classFqnRange.substring(line).trim()
    val fileName = parsedLineInfo.fileName
    val resolvedInfo = exceptionInfoCache.resolveClassOrFile(className, fileName)

    // TODO: if the class is not really resolvable, there's a chance the class is
    //   "stale" (renamed or deleted), maybe we can do better in the future as we have
    //   the VCS info.
    if (resolvedInfo.isInLibrary || resolvedInfo.classes.isEmpty()) return null

    val contextDataForDiff =
      createContextDataForDiff(foundVcsInfo, resolvedInfo.classes.keys.toList(), lineNumber)
        ?: return null

    // Here, we attach inlay element to the "file name and line number" part,
    // e.g. "Foo.java:17" in " at com.project.module.Foo.bar(Foo.java:17)".
    val textStartOffset = textEndOffset - line.length
    val highlightStartOffset: Int = textStartOffset + parsedLineInfo.fileLineRange.startOffset
    val highlightEndOffset: Int = textStartOffset + parsedLineInfo.fileLineRange.endOffset

    val diffLinkInlayResult =
      DiffLinkInlayResult(contextDataForDiff, highlightStartOffset, highlightEndOffset, tracker)

    return Filter.Result(listOf(diffLinkInlayResult))
  }

  data class DiffLinkInlayResult(
    private val diffContextData: ContextDataForDiff,
    private val highlightStartOffset: Int,
    private val highlightEndOffset: Int,
    private val tracker: AppInsightsTracker,
  ) : ResultItem(highlightStartOffset, highlightEndOffset, null), InlayProvider {
    override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
      val factory = PresentationFactory(editor as EditorImpl)
      val inlayPresentation = factory.createInlayPresentation(editor)

      return PresentationRenderer(inlayPresentation)
    }

    private fun PresentationFactory.createInlayPresentation(editor: Editor): InlayPresentation {
      val commaInlay =
        InsightsTextInlayPresentation(
            text = ", ",
            textAttributesKey = CodeInsightColors.HYPERLINK_ATTRIBUTES,
            isUnderline = false,
            editor,
          )
          .withLineCentered(editor)

      val showDiffTooltip =
        HelpTooltip().apply {
          setDescription(TOOLTIP_TEXT)
          setLink("") {} // required due to bug in HelpTooltip
          setBrowserLink("More info", URL(VCS_INTEGRATION_LEARN_MORE_URL))
        }

      val showDiffInlay =
        InsightsTextInlayPresentation(
            text = INLAY_DIFF_LINK_DISPLAY_TEXT,
            textAttributesKey = CodeInsightColors.HYPERLINK_ATTRIBUTES,
            isUnderline = true,
            editor,
          )
          .withLineCentered(editor)
          .withOnClick(this) { _, _ ->
            val project = editor.project ?: return@withOnClick
            goToDiff(diffContextData, project)
            logActivity()
          }
          .withHandCursor(editor)
          .withTooltip(showDiffTooltip, factory = this)

      return seq(commaInlay, showDiffInlay)
    }

    private fun logActivity() {
      val metricsEventBuilder =
        AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.newBuilder().apply {
          clickLocation =
            AppQualityInsightsUsageEvent.AppQualityInsightsStacktraceDetails.ClickLocation
              .DIFF_INLAY
        }

      tracker.logStacktraceClicked(null, metricsEventBuilder.build())
    }
  }

  companion object {
    private const val INLAY_DIFF_LINK_DISPLAY_TEXT = "show diff"
    private const val TOOLTIP_TEXT =
      "Show the difference between the historical source from the app version referenced in the issue and the current source."
  }
}

/**
 * Custom inlay text presentation for insights specific.
 *
 * The basic idea is from [TextInlayPresentation], just we can apply our own style instead of the
 * typical "inlay" style.
 */
class InsightsTextInlayPresentation(
  val text: String,
  private val textAttributesKey: TextAttributesKey,
  private val isUnderline: Boolean,
  private val editor: Editor,
) : BasePresentation() {
  private var normalTextMetrics: InlayTextMetrics? = null
  override val width: Int
    get() = getOrCreateMetrics().getStringWidth(text)

  override val height: Int
    get() = getOrCreateMetrics().fontHeight

  private val colorsScheme
    get() = editor.colorsScheme

  private val normalTextSize
    get() = colorsScheme.editorFontSize2D

  private val familyName
    get() = colorsScheme.editorFontName

  private fun getOrCreateMetrics(): InlayTextMetrics {
    if (normalTextMetrics == null || !normalTextMetrics!!.isActual(normalTextSize, familyName)) {
      normalTextMetrics = createMetrics(editor, normalTextSize)
    }

    return normalTextMetrics!!
  }

  private fun createMetrics(editor: Editor, size: Float): InlayTextMetrics {
    val fontType = colorsScheme.getAttributes(textAttributesKey).fontType
    val editorFont = EditorUtil.getEditorFont()
    var font = editorFont.deriveFont(fontType, size)

    if (isUnderline) {
      font =
        font.deriveFont(font.attributes + (TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
    }

    val context = getCurrentContext(editor.contentComponent)
    val metrics = FontInfo.getFontMetrics(font, context)
    // We assume this will be a better approximation to a real line height for a given font
    val fontHeight = ceil(font.createGlyphVector(context, "H").visualBounds.height).toInt()

    return InlayTextMetrics(editor, fontHeight, fontHeight, metrics, fontType)
  }

  private fun getCurrentContext(editorComponent: JComponent): FontRenderContext {
    val editorContext = FontInfo.getFontRenderContext(editorComponent)
    return FontRenderContext(
      editorContext.transform,
      AntialiasingType.getKeyForCurrentScope(false),
      UISettings.editorFractionalMetricsHint,
    )
  }

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val savedHint = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)

    try {
      val metrics = getOrCreateMetrics()
      val font = metrics.font
      g.font = font
      g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        AntialiasingType.getKeyForCurrentScope(false),
      )
      g.color = colorsScheme.getAttributes(textAttributesKey).foregroundColor
      g.drawString(text, 0, metrics.fontBaseline)
    } finally {
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
    }
  }

  override fun toString(): String = "InsightsTextInlayPresentation(text = $text)"
}
