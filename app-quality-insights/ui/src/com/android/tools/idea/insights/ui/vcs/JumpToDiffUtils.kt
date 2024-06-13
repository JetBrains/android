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

import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.vcs.VcsForAppInsights
import com.android.tools.idea.insights.vcs.createShortRevisionString
import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.DiffPreviewProvider
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private fun getLogger() =
  Logger.getInstance("com.android.tools.idea.insights.ui.vcs.JumpToDiffUtils")

/**
 * Context data for requesting a diff view.
 *
 * @param vcsKey denotes the VCS type of the commit.
 * @param revision denotes the commit we are diffing with.
 * @param filePath is for locating the revision content and the current content.
 * @param lineNumber (1-based) is for placing caret at the line where the crash is captured.
 * @param origin is to scope the request
 */
data class ContextDataForDiff(
  val vcsKey: VCS_CATEGORY,
  val revision: String,
  val filePath: FilePath,
  val lineNumber: Int,
  val origin: Connection?,
)

/**
 * Brings up a diff view.
 *
 * The left panel is for the historical source file from the given commit in the [context]. The
 * right panel is for the current source file.
 *
 * The caret is placed on the line of the left panel where the crash is associated to.
 */
fun goToDiff(context: ContextDataForDiff, project: Project) {
  val provider = InsightsDiffViewProvider(context, project)
  val diffVFile =
    InsightsDiffVirtualFile(provider).also {
      // This is needed in unit tests where a non-text editor should be created, see
      // TestEditorManagerImpl#openFileImpl3.
      it.putUserData(
        FileEditorProvider.KEY,
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList.first { ex ->
          ex.accept(project, it)
        },
      )
    }

  val fileEditor =
    FileEditorManager.getInstance(project).openFile(diffVFile, true).first()
      as DiffRequestProcessorEditor

  // If it's the already opened file, the previously scrolled position is preserved and might not
  // be what we want. So we scroll to the desired position ourselves.
  invokeLater { fileEditor.scrollToLocation(context) }
}

private fun DiffRequestProcessorEditor.scrollToLocation(context: ContextDataForDiff) {
  val viewer = processor.activeViewer ?: return
  when (viewer) {
    is SimpleDiffViewer -> {
      viewer.currentSide = Side.LEFT
      DiffUtil.scrollEditor(viewer.getEditor(Side.LEFT), context.lineNumber - 1, true)
    }
    is UnifiedDiffViewer -> {
      // The right (current src) side is the master side and this can't be changed. So we need to
      // map the line number with our best efforts. This is not desirable but should be rare.
      val approximateLocation = viewer.transferLineToOneside(Side.LEFT, context.lineNumber - 1)
      DiffUtil.scrollEditor(viewer.editor, approximateLocation, true)
    }
    else -> {
      getLogger().warn("Not supported viewer type: $viewer")
      return
    }
  }
}

class InsightsDiffVirtualFile(val provider: InsightsDiffViewProvider) :
  PreviewDiffVirtualFile(provider)

data class InsightsDiffViewProvider(val insightsContext: ContextDataForDiff, val project: Project) :
  DiffPreviewProvider {

  data class OwnerObject(
    val vcsKey: VCS_CATEGORY,
    val revision: String,
    val filePath: FilePath,
    val project: Project,
  )

  override fun createDiffRequestProcessor(): DiffRequestProcessor {
    // Note this object will be disposed when the corresponding DiffRequestProcessorEditor is
    // disposed by the DiffEditorProvider when the editor is closed.
    return object : CacheDiffRequestProcessor<ChangeDiffRequestProducer>(project) {
      override fun getRequestName(provider: ChangeDiffRequestProducer): String {
        return provider.name
      }

      override fun getCurrentRequestProvider(): ChangeDiffRequestProducer? {
        return ChangeDiffRequestProducer.create(project, change, viewConfiguration)
      }

      override fun loadRequest(
        provider: ChangeDiffRequestProducer,
        indicator: ProgressIndicator,
      ): DiffRequest {
        val request = provider.process(context, indicator)

        return if (request is ErrorDiffRequest) {
          // Show more user-friendly error message.
          val message = "Source revision is not available. Update your working tree and try again."
          thisLogger().warn(message + "(original message: ${request.message})")
          ErrorDiffRequest(request.title, message, request.producer, request.exception)
        } else {
          request
        }
      }
    }
  }

  override fun getOwner(): Any {
    // We will bring up the existing diff view if the associated historical source file is the same.
    return OwnerObject(
      vcsKey = insightsContext.vcsKey,
      revision = insightsContext.revision,
      filePath = insightsContext.filePath,
      project,
    )
  }

  override fun getEditorTabName(processor: DiffRequestProcessor?): String {
    return insightsContext.filePath.name
  }

  private val viewConfiguration =
    mapOf<Key<*>, Any>(
      // Customize titles.
      DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER to
        listOfNotNull(
          createEditorTitleFromContext(insightsContext, project),
          createEditorTitle("Current source"),
        ),
      // Customize caret place
      DiffUserDataKeys.SCROLL_TO_LINE to Pair.create(Side.LEFT, insightsContext.lineNumber - 1),
      // Customize diff alignment.
      DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF to true,
    )

  private val change: Change
    get() {
      val vcsContent =
        VcsForAppInsights.getExtensionByKey(insightsContext.vcsKey)
          ?.createVcsContent(insightsContext.filePath, insightsContext.revision, project)

      return createChangesWithCurrentContentForFile(insightsContext.filePath, vcsContent).single()
    }
}

private fun createEditorTitle(title: String): DiffEditorTitleCustomizer {
  return DiffEditorTitleCustomizer { JBLabel(title) }
}

private fun createEditorTitleFromContext(
  vcsContext: ContextDataForDiff,
  project: Project,
): DiffEditorTitleCustomizer {
  val shortVcsRevisionNumber = createShortRevisionString(vcsContext.vcsKey, vcsContext.revision)

  val displayText =
    "<html>Historical source at commit: <a href=''>${shortVcsRevisionNumber}</a> " +
      "<i>(Source at the app version referenced in the issue)</i></html>"

  return DiffEditorTitleCustomizer {
    object : JBLabel(displayText) {
        override fun createHyperlinkListener(): HyperlinkListener {
          return object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
              jumpToRevision(project, vcsContext.revision)
            }
          }
        }
      }
      .apply { setCopyable(true) }
  }
}
