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
package com.android.tools.idea.dagger

import com.android.tools.idea.dagger.DaggerRelatedItemLineMarkerProvider.Companion.getGotoItems
import com.android.tools.idea.dagger.concepts.getDaggerElement
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.intellij.codeInsight.navigation.getRelatedItemsPopup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

/**
 * Action which directly navigates to the Dagger element related to the element at the cursor (or
 * opens a navigation popup if there are multiple related elements).
 *
 * This action is not directly used anywhere, but exists so that users can bind a keystroke to it if
 * they wish.
 */
class GoToRelatedDaggerItemsAction : EditorAction(Handler()) {
  private class Handler : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val project = editor.project ?: return

      // doExecute is called with a null Caret, so we have to get the current caret instead.
      val gotoItems = editor.caretModel.currentCaret.getGotoItems(project)
      when (gotoItems.size) {
        0 -> return
        1 -> gotoItems.first().navigate()
        else ->
          getRelatedItemsPopup(gotoItems, DaggerBundle.message("dagger.related.items.popup.title"))
            .showInBestPositionFor(editor)
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?) = true

    private fun Caret.getGotoItems(project: Project) =
      PsiDocumentManager.getInstance(project)
        .getPsiFile(editor.document)
        ?.findElementAt(offset)
        ?.parent
        ?.getDaggerElement()
        ?.getGotoItems()
        .orEmpty()
  }
}
