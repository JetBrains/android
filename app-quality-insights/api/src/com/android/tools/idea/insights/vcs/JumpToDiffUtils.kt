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

import com.android.tools.idea.insights.VCS_CATEGORY
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel

/**
 * Context data for showing the diff view.
 *
 * @param vcsKey denotes the VCS type of the commit.
 * @param revision denotes the commit we are diffing with.
 * @param filePath is for locating the revision content and the current content.
 * @param lineNumber (1-based) is for placing caret at the line where the crash is captured.
 */
data class ContextDataForDiff(
  val vcsKey: VCS_CATEGORY,
  val revision: String,
  val filePath: FilePath,
  val lineNumber: Int
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
  val requestChain =
    object : ChangeDiffRequestChain.Async() {
      override fun loadRequestProducers(): ListSelection<out ChangeDiffRequestChain.Producer> {
        try {
          val changeContext =
            mapOf<Key<*>, Any>(
              // Customize titles.
              DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER to
                listOfNotNull(
                  createEditorTitleFromContext(context, project),
                  createEditorTitle("Your version")
                ),
              // Customize caret place
              DiffUserDataKeys.SCROLL_TO_LINE to Pair.create(Side.LEFT, context.lineNumber - 1),
              // Customize diff alignment.
              DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF to true
            )

          val chained =
            changes
              .mapNotNull { ChangeDiffRequestProducer.create(project, it, changeContext) }
              .toList()

          return ListSelection.create(chained, null)
        } catch (exception: Exception) {
          // Rethrow, then the exception message would be printed out in the diff view.
          throw DiffRequestProducerException(exception)
        }
      }

      private val changes: List<Change>
        get() {
          val vcsContext =
            VcsForAppInsights.getExtensionByKey(context.vcsKey)
              ?.createVcsContent(context.vcsKey, context.filePath, context.revision, project)

          return createChangesWithCurrentContentForFile(context.filePath, vcsContext)
        }
    }

  // TODO: Should bring up an existing window if there's instead of creating a new one.
  DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
}

private fun createEditorTitle(title: String): DiffEditorTitleCustomizer {
  return DiffEditorTitleCustomizer { JBLabel(title) }
}

private fun createEditorTitleFromContext(
  vcsContext: ContextDataForDiff,
  project: Project
): DiffEditorTitleCustomizer {
  val shortVcsRevisionNumber =
    VcsForAppInsights.getExtensionByKey(vcsContext.vcsKey)
      ?.getShortRevisionFromString(vcsContext.vcsKey, vcsContext.revision)

  val displayText = "File from the affected commit: <hyperlink>$shortVcsRevisionNumber</hyperlink>"

  return DiffEditorTitleCustomizer {
    HyperlinkLabel().apply {
      setTextWithHyperlink(displayText)
      this.addHyperlinkListener { jumpToRevision(project, vcsContext.revision) }
    }
  }
}
