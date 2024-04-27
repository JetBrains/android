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
package com.android.tools.compose.code.state

import com.android.tools.compose.ComposeBundle
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.function.RunOnce
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_NAME =
  "ComposeStateReadScopeHighlightingTextAttributes"
val COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
  TextAttributesKey.createTextAttributesKey(
    COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_NAME,
    DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE
  )

@VisibleForTesting internal val HIGHLIGHT_FLASH_DURATION = 100.milliseconds
@VisibleForTesting internal const val HIGHLIGHT_FLASH_COUNT = 2

/** [InlayHintsProvider] for `State` reads in Compose. */
class ComposeStateReadInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (file !is KtFile) return null
    if (!StudioFlags.COMPOSE_STATE_READ_INLAY_HINTS_ENABLED.get()) return null
    return ComposeStateReadInlayHintsCollector
  }

  companion object {
    const val PROVIDER_ID = "compose.state.read"
  }
}

/** Hints collector that adds a simple "State read" inlay hint after a `State` read. */
object ComposeStateReadInlayHintsCollector : SharedBypassCollector {
  override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
    if (element !is KtNameReferenceExpression) return
    val stateRead = element.getStateRead() ?: return
    val position = InlineInlayPosition(element.endOffset, relatedToPrevious = true)
    val tooltip =
      ComposeBundle.message("state.read.message", stateRead.stateVar.text, stateRead.scopeName)
    sink.addPresentation(position, tooltip = tooltip, hasBackground = true) {
      val actionData =
        InlayActionData(
          PsiPointerInlayActionPayload(SmartPointerManager.createPointer(stateRead.scope)),
          ComposeStateReadInlayActionHandler.HANDLER_NAME
        )
      text(ComposeBundle.message("state.read"), actionData)
    }
  }
}

/** Applies scope highlighting to [segment], returning the [RangeHighlighter] created. */
private fun MarkupModel.highlight(segment: Segment): RangeHighlighter =
  addRangeHighlighter(
    COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY,
    segment.startOffset,
    segment.endOffset,
    HighlighterLayer.ELEMENT_UNDER_CARET - 1,
    HighlighterTargetArea.EXACT_RANGE
  )

/** Runs [block] the next time this [Editor] has a caret added/moved/removed. */
private fun Editor.runAtNextCaretChange(block: () -> Unit) {
  with(caretModel) {
    addCaretListener(
      object : CaretListener {
        private val payload = RunOnce {
          removeCaretListener(this)
          block.invoke()
        }

        override fun caretPositionChanged(event: CaretEvent) {
          payload()
        }

        override fun caretAdded(event: CaretEvent) {
          payload()
        }

        override fun caretRemoved(event: CaretEvent) {
          payload()
        }
      }
    )
  }
}

/** Applies scope highlighting to [range] until the next time the caret moves. */
private fun Editor.highlightUntilCaretMovement(range: Segment) {
  with(markupModel) { highlight(range).also { runAtNextCaretChange { removeHighlighter(it) } } }
}

private suspend fun Editor.highlightForDuration(range: Segment, duration: Duration) {
  val highlighter = markupModel.highlight(range)
  delay(duration)
  markupModel.removeHighlighter(highlighter)
}

/** Handler invoked when users click on the `State` read inlay hints. */
class ComposeStateReadInlayActionHandler(private val scope: CoroutineScope) : InlayActionHandler {
  override fun handleClick(editor: Editor, payload: InlayActionPayload) {
    val range = (payload as? PsiPointerInlayActionPayload)?.pointer?.range ?: return
    scope.launch {
      repeat(HIGHLIGHT_FLASH_COUNT) {
        editor.highlightForDuration(range, HIGHLIGHT_FLASH_DURATION)
        delay(HIGHLIGHT_FLASH_DURATION)
      }
      editor.highlightUntilCaretMovement(range)
    }
  }

  companion object {
    const val HANDLER_NAME = ComposeStateReadInlayHintsProvider.PROVIDER_ID
  }
}
