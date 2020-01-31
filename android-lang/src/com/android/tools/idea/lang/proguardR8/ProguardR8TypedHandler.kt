/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8PsiFile
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ProguardR8TypedHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file is ProguardR8PsiFile) {
      // Allow to start autocompletion without pressing Ctrl+Space.
      // By default AutoPopup appears only if typed character is digit or letter.
      // '-' for flags; '<' for <methods>, <fields> ,...
      if (charTyped == '-' || charTyped == '<') {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
      }
    }
    return super.checkAutoPopup(charTyped, project, editor, file)
  }
}
