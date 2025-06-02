/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections

import com.android.SdkConstants.DOT_PROPERTIES
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.lint.AndroidLintBundle
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.AndroidQuickfixContexts.ContextType
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.detector.api.Incident
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class AndroidLintAndroidGradlePluginVersionInspection :
  AndroidLintInspectionBase(
    AndroidLintBundle.message("android.lint.inspections.android.gradle.plugin.version"),
    GradleDetector.AGP_DEPENDENCY,
  ) {

  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    incident: Incident,
  ): Array<LintIdeQuickFix> {
    val fixes = super.getQuickFixes(startElement, endElement, incident)
    return when {
      // gradle wrapper update: don't link to the AGP upgrade assistant
      incident.file.path.endsWith(DOT_PROPERTIES) -> fixes
      !LintIdeSupport.get().shouldRecommendUpdateAgp(startElement.project) -> fixes
      else ->
        arrayOf(
          InvokeAGPUpgradeAssistantQuickFix(
            LintIdeSupport.get().recommendedAgpVersion(startElement.project)
          ),
          *fixes,
        )
    }
  }

  class InvokeAGPUpgradeAssistantQuickFix(agpVersion: AgpVersion?) :
    DefaultLintQuickFix(
      "Invoke AGP Upgrade Assistant${if (agpVersion != null) " for upgrade to $agpVersion" else ""}"
    ) {
    override fun apply(
      startElement: PsiElement,
      endElement: PsiElement,
      context: AndroidQuickfixContexts.Context,
    ) {
      LintIdeSupport.get().updateAgp(startElement.project)
    }

    override fun generatePreview(
      project: Project,
      editor: Editor,
      file: PsiFile,
    ): IntentionPreviewInfo? {
      return IntentionPreviewInfo.EMPTY
    }

    override fun isApplicable(
      startElement: PsiElement,
      endElement: PsiElement,
      contextType: ContextType,
    ): Boolean = true
  }
}
