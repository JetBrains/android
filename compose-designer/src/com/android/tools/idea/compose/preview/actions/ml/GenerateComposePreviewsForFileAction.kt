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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class GenerateComposePreviewsForFileAction :
  GenerateComposePreviewBaseAction(message("action.generate.previews.for.file")) {
  override fun isActionFlagEnabled() = StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE.get()

  override fun getTargetComposableFunctions(e: AnActionEvent): List<KtNamedFunction> {
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return emptyList()
    // Collect all the valid Composables from this file
    return psiFile.collectDescendantsOfType<KtNamedFunction> { it.isValidComposableFunction() }
  }
}
