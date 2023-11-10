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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

const val COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_NAME =
  "ComposeStateReadScopeHighlightingTextAttributes"
val COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
  TextAttributesKey.createTextAttributesKey(
    COMPOSE_STATE_READ_SCOPE_HIGHLIGHTING_TEXT_ATTRIBUTES_NAME,
    DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE
  )

/** [InlayHintsProvider] for `State` reads in Compose. */
class ComposeStateReadInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    if (file !is KtFile) return null
    if (!StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.get()) return null
    return ComposeStateReadInlayHintsCollector
  }

  companion object {
    const val PROVIDER_ID = "compose.state.read"
  }
}

object ComposeStateReadInlayHintsCollector : SharedBypassCollector {
  override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
    // Does nothing yet
  }
}
