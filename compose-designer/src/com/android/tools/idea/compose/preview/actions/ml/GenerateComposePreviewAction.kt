/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions.ml

import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Action to generate a Compose Preview for a target Composable function. */
class GenerateComposePreviewAction :
  GenerateComposePreviewBaseAction(message("action.generate.preview")) {

  override fun isActionFlagEnabled(): Boolean = StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.get()

  override fun getTargetComposableFunctions(e: AnActionEvent): List<KtNamedFunction> {
    // Check if the function where the caret is located is a valid Composable.
    // Return it in a singleton list if so, otherwise return an empty list.
    val containingFunction = getContainingFunctionAtCaret(e)
    return if (containingFunction?.isValidComposableFunction() == true) listOf(containingFunction)
    else emptyList()
  }

  private fun getContainingFunctionAtCaret(e: AnActionEvent): KtNamedFunction? {
    val caret = e.getData(CommonDataKeys.CARET) ?: return null
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
    val psiElement = runReadAction { psiFile.findElementAt(caret.offset) } ?: return null
    return psiElement.parentOfType<KtNamedFunction>()
  }
}
