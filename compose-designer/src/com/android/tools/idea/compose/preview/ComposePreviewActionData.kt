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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.stdui.ActionData
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.Icon

/**
 * Creates an [ActionData] to invoke the given [AnAction].
 *
 * @param action the [AnAction] to be invoked when the action is performed
 * @param psiFilePointer a [SmartPsiElementPointer] to the file containing the preview
 * @param mainSurface the [DesignSurface] of the preview
 * @param text the text to be displayed for the action
 * @param icon the [Icon] to be displayed for the action
 */
internal fun createPreviewActionData(
  action: AnAction,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  mainSurface: DesignSurface<*>,
  text: String,
  icon: Icon?,
): ActionData {
  return ActionData(text, icon) {
    val psiFile = psiFilePointer.element ?: return@ActionData
    val selectedEditor =
      (FileEditorManager.getInstance(psiFile.project).selectedEditor as? TextEditorWithPreview)
        ?.editor ?: return@ActionData
    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PSI_FILE, psiFile)
        .add(CommonDataKeys.EDITOR, selectedEditor)
        .add(CommonDataKeys.PROJECT, psiFile.project)
        .add(DESIGN_SURFACE, mainSurface)
        .build()
    val event =
      AnActionEvent.createEvent(
        action,
        dataContext,
        null,
        ActionPlaces.UNKNOWN,
        ActionUiKind.NONE,
        null,
      )
    action.actionPerformed(event)
  }
}
