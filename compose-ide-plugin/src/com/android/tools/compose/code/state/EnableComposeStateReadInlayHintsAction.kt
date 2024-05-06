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
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock

/** Turns on inlay hints for Compose `State` reads. */
internal object EnableComposeStateReadInlayHintsAction :
  IntentionAction by ComposeStateReadInlayHintsAction(true)

/** Enables or disables inlay hints for Compose `State` reads. */
private class ComposeStateReadInlayHintsAction(private val enable: Boolean) : IntentionAction {
  private val msgText =
    if (enable) {
      ComposeBundle.message("state.read.inlay.provider.enable")
    } else {
      ComposeBundle.message("state.read.inlay.provider.disable")
    }

  override fun getFamilyName(): String = getText()

  override fun getText() = msgText

  override fun startInWriteAction(): Boolean = true

  @RequiresReadLock
  override fun isAvailable(project: Project, editor: Editor, file: PsiFile) =
    hintsEnabled() != enable

  @RequiresWriteLock
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    DeclarativeInlayHintsSettings.getInstance()
      .setProviderEnabled(ComposeStateReadInlayHintsProvider.PROVIDER_ID, enable)
    DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
  }

  private fun hintsEnabled() =
    DeclarativeInlayHintsSettings.getInstance()
      .isProviderEnabled(ComposeStateReadInlayHintsProvider.PROVIDER_ID) ?: false
}
