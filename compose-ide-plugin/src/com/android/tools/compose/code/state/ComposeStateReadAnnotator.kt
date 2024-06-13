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
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

const val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME = "ComposeStateReadTextAttributes"

val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
  TextAttributesKey.createTextAttributesKey(
    COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME,
    DefaultLanguageHighlighterColors.FUNCTION_CALL,
  )

/**
 * Annotator that highlights reads of `androidx.compose.runtime.State` variables inside
 * `@Composable` functions.
 *
 * TODO(b/225218822): Before productionizing this, we must determine whether to remove the ability
 *   to highlight or change this to use `KotlinHighlightingVisitorExtension` (to avoid race
 *   conditions). This may be non-trivial as that class does not have the ability to highlight
 *   anything other than the element being visited.
 */
class ComposeStateReadAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.COMPOSE_STATE_READ_INLAY_HINTS_ENABLED.get()) return
    if (element !is KtNameReferenceExpression) return
    element.getStateRead()?.let {
      val msg = ComposeBundle.message("state.read.message.titled", it.stateVar.text, it.scopeName)
      holder
        .newAnnotation(HighlightSeverity.INFORMATION, msg)
        .textAttributes(COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
        .withFix(EnableComposeStateReadInlayHintsAction)
        .create()
    }
  }
}
