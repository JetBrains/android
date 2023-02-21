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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.lint.AndroidLintBundle
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.AndroidQuickfixContexts.ContextType
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.intellij.psi.PsiElement
import java.util.ArrayList

class AndroidLintAndroidGradlePluginVersionInspection :
  AndroidLintInspectionBase(
    AndroidLintBundle.message("android.lint.inspections.android.gradle.plugin.version"),
    GradleDetector.AGP_DEPENDENCY
  ) {
  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    fixData: LintFix?
  ): Array<LintIdeQuickFix> {
    val quickFixes = ArrayList<LintIdeQuickFix>()
    // Find and add a quick fix corresponding to a "safe" (micro-level change only) AGP upgrade
    if (fixData is LintFixGroup && fixData.type == LintFix.GroupType.ALTERNATIVES) {
      fixData.fixes
        .asSequence()
        .filter { it.robot }
        .forEach { fix ->
          quickFixes.addAll(super.getQuickFixes(startElement, endElement, message, fix))
        }
    } else if (fixData != null && fixData.robot) {
      quickFixes.addAll(super.getQuickFixes(startElement, endElement, message, fixData))
    }
    if (get().shouldRecommendUpdateAgpToLatest(startElement.project)) {
      val recommendedVersion = get().recommendedAgpVersion(startElement.project)
      val auaQuickFix: LintIdeQuickFix = InvokeAGPUpgradeAssistantQuickFix(recommendedVersion)
      quickFixes.add(auaQuickFix)
    }
    return quickFixes.toArray(LintIdeQuickFix.EMPTY_ARRAY)
  }

  class InvokeAGPUpgradeAssistantQuickFix(agpVersion: AgpVersion?) :
    DefaultLintQuickFix(
      if (agpVersion == null) "Invoke Upgrade Assistant"
      else "Invoke Upgrade Assistant for upgrade to $agpVersion"
    ) {
    override fun apply(
      startElement: PsiElement,
      endElement: PsiElement,
      context: AndroidQuickfixContexts.Context
    ) {
      get().updateAgpToLatest(startElement.project)
    }

    override fun isApplicable(
      startElement: PsiElement,
      endElement: PsiElement,
      contextType: ContextType
    ): Boolean = true
  }
}
