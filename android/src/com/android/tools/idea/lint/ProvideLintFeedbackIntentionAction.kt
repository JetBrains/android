/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nls

class ProvideLintFeedbackIntentionAction(private val myIssue: String) : IntentionAction {

  @Nls(capitalization = Nls.Capitalization.Sentence)
  override fun getText(): String {
    return "Provide feedback on this warning"
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  override fun getFamilyName(): String {
    // Don't want to collapse these across issue types so ensure that the message is unique for each
    return "Provide feedback on issues of type $myIssue"
  }

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    return ProvideLintFeedbackPanel.canRequestFeedback()
  }

  @Throws(IncorrectOperationException::class)
  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val dialog = ProvideLintFeedbackPanel(project, myIssue)
    dialog.show()
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}
