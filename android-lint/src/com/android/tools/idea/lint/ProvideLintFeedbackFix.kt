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

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class ProvideLintFeedbackFix(private val myIssue: String) : LocalQuickFix {

  @Nls(capitalization = Nls.Capitalization.Sentence)
  override fun getName(): String {
    return "Provide feedback on this warning"
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  override fun getFamilyName(): String {
    // Don't want to collapse these across issue types so ensure that the message is unique for each
    return "Provide feedback on issues of type $myIssue"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    ApplicationManager.getApplication()
      .invokeLater(
        {
          if (!project.isDisposed) {
            val dialog = ProvideLintFeedbackPanel(project, myIssue)
            dialog.show()
          }
        },
        ModalityState.any()
      )
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}
