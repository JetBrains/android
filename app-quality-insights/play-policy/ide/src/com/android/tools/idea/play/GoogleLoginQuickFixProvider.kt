/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.play

import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.LintIdeQuickFixProvider
import com.android.tools.idea.lint.isPlayPolicyIssue
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.fstLoginFeature
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiElement
import kotlinx.coroutines.launch

class GoogleLoginQuickFixProvider : LintIdeQuickFixProvider {
  class GoogleLoginIdeQuickFix : DefaultLintQuickFix("Sign in to Google Account") {
    override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
      // Invoke Android Studio's authentication stack / onboarding dialog
      if (!fstLoginFeature.isLoggedIn()) {
        val project = startElement.project
        val psiFile = startElement.containingFile
        project.coroutineScope.launch {
          GoogleLoginService.instance.logIn()
          // Re-run lint checks on this file immediately.
          readAction {
            if (psiFile.isValid) {
              DaemonCodeAnalyzer.getInstance(project).restart(psiFile, "login fix applied")
            }
          }
        }
      }
    }

    override fun startInWriteAction(): Boolean = false
  }

  override fun getQuickFixes(
    issue: Issue,
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    fixData: LintFix?,
  ): Array<LintIdeQuickFix> {
    if (StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.get() && isPlayPolicyIssue(issue)) {
      if (!fstLoginFeature.isLoggedIn()) {
        return arrayOf(GoogleLoginIdeQuickFix())
      }
    }
    return LintIdeQuickFix.EMPTY_ARRAY
  }
}
