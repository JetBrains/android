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
package com.android.tools.idea.lint.common

import com.android.tools.idea.lint.common.LintBundle.Companion.message
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.LintFix
import com.intellij.psi.PsiElement

class AndroidLintBaselineInspection :
  AbstractBaselineInspection(
    message("android.lint.inspections.lint.baseline"),
    IssueRegistry.BASELINE_USED,
  ) {
  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    fixData: LintFix?,
  ): Array<LintIdeQuickFix> {
    return arrayOf(
      object : DefaultLintQuickFix("Temporarily turn off the baseline and re-run the analysis") {
        override fun apply(
          startElement: PsiElement,
          endElement: PsiElement,
          context: AndroidQuickfixContexts.Context,
        ) {
          ourSkipBaselineNextRun = true
          rerun()
        }
      }
    )
  }
}
