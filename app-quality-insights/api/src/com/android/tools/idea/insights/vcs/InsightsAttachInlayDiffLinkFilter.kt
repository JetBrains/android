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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppVcsInfo
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionWorker.parseExceptionLine
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.InlayProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Custom filter for attaching inlay diff links for traces if applicable.
 *
 * A diff (historical source file from the affected commit VS current source file) link is attached
 * just after the file line number when
 * 1) the class/file extracted from a trace line is navigable/resolvable and within project scope,
 * 2) an app VCS info is found (this piece of info is captured when an app is built and is sent
 *    along with a crash)
 */
class InsightsAttachInlayDiffLinkFilter(private val containingConsole: ConsoleViewImpl) : Filter {
  private val project = containingConsole.project
  private val cache = ExceptionInfoCache(project, GlobalSearchScope.allScope(project))

  private fun fetchVcsInfo(): AppVcsInfo? {
    if (!StudioFlags.APP_INSIGHTS_VCS_SUPPORT.get()) return null

    return (containingConsole.getClientProperty(VCS_INFO_OF_SELECTED_CRASH) as? AppVcsInfo)
      ?.takeUnless { it == AppVcsInfo.NONE }
  }

  private fun createContextDataForDiff(
    appVcsInfo: AppVcsInfo,
    virtualFiles: List<VirtualFile>,
    lineNumber: Int
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
          lineNumber = lineNumber
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
    val resolvedInfo = cache.resolveClassOrFile(className, fileName)

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
      DiffLinkInlayResult(contextDataForDiff, highlightStartOffset, highlightEndOffset)

    return Filter.Result(listOf(diffLinkInlayResult))
  }
}

private const val INLAY_DIFF_LINK_DISPLAY_TEXT = "or diff with the historical source ↗"

private class DiffLinkInlayResult(
  private val diffContextData: ContextDataForDiff,
  highlightStartOffset: Int,
  highlightEndOffset: Int
) : ResultItem(highlightStartOffset, highlightEndOffset, null), InlayProvider {
  override fun createInlayRenderer(editor: Editor): EditorCustomElementRenderer {
    val factory = PresentationFactory(editor as EditorImpl)
    val inlayPresentation =
      factory.roundWithBackground(factory.smallText(INLAY_DIFF_LINK_DISPLAY_TEXT))

    val presentation =
      factory.referenceOnHover(inlayPresentation) { _: MouseEvent, _: Point? ->
        val project = editor.project ?: return@referenceOnHover
        goToDiff(diffContextData, project)
      }

    return PresentationRenderer(presentation)
  }
}
