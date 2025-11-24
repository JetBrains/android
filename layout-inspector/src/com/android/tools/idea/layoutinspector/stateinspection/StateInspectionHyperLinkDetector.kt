/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.stateinspection

import com.intellij.codeInsight.navigation.actions.ClickLinkAction
import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.impl.EditorHyperlinkListener
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Expirable
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

private const val CLICK_LINK_ACTION_ID = "ClickLink"

internal class StateInspectionHyperLinkDetectorFactory : HyperLinkDetectorFactory {
  override fun create(
    editor: EditorEx,
    scope: CoroutineScope,
    activatedLinkListener: EditorHyperlinkListener,
  ): HyperLinkDetector = StateInspectionHyperLinkDetector(editor, scope, activatedLinkListener)
}

/** A Hyperlink detector that adds hyperlinks to an [Editor] */
internal open class StateInspectionHyperLinkDetector(
  private val editor: EditorEx,
  scope: CoroutineScope,
  private val activatedLinkListener: EditorHyperlinkListener,
) : HyperLinkDetector {
  private val project = editor.project!!
  private val editorHyperlinkSupport = EditorHyperlinkSupport.get(editor)
  private val filter = CompositeFilter(project)
  private val expirableToken = Expirable { editor.isDisposed }

  @TestOnly val filterJob: Job

  init {
    filterJob =
      scope.launch {
        // Add all standard filters (this will include hyperlinks of the fileName of each line)
        // Performed as a background task based on `ConsoleViewImpl.updatePredefinedFiltersLater()`
        // TODO: Remove filterJob when Intellij allows us to specify a testDispatcher in a
        // readAction.
        val filters =
          smartReadAction(project) {
            ConsoleViewUtil.computeConsoleFilters(
              project,
              null,
              GlobalSearchScope.allScope(project),
            )
          }
        // Allow custom extensions to add hyperlinks:
        LayoutInspectorStateInspectionFilterProvider.EP_NAME.extensionList
          .map { it.create(editor) }
          .forEach { filter.addFilter(it) }

        filters.forEach { filter.addFilter(it) }

        replaceClickLinkAction()
      }

    // addEditorHyperlinkListener is marked @ApiStatus.Internal, but there doesn't seem
    // to be a different way to track these hyperlink events.
    editorHyperlinkSupport.addEditorHyperlinkListener(activatedLinkListener)
  }

  override fun detectHyperlinks() {
    // The state reads is static content, so we will always detect links in the entire document:
    val startLine = 0
    val endLine = editor.document.getLineNumber(editor.document.textLength)
    editorHyperlinkSupport.highlightHyperlinksLater(filter, startLine, endLine, expirableToken)
  }

  private fun replaceClickLinkAction() {
    val manager = ActionManager.getInstance()
    val action = manager.getAction(CLICK_LINK_ACTION_ID)
    if (action is ClickLinkAction) {
      // Hack: Replace the ClickLinkAction to provide logging of keyboard activated hyperlinks.
      // Note: Contrary to the name, this is not used for mouse clicks.
      // See b/458791627. Remove this if IJPL-217477 provides a different way to track link actions.
      manager.replaceAction(CLICK_LINK_ACTION_ID, ClickLinkActionWithLogging())
    }
    editor.putUserData(CLICK_LINK_LOGGING_KEY, activatedLinkListener)
  }
}
