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
package com.android.tools.idea.lint.inspections

import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.lint.common.LintIdeQuickFixProvider
import com.android.tools.idea.lint.quickFixes.JumpToIntersectingDeepLinkFix
import com.android.tools.lint.checks.AppLinksValidDetector
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.psi.PsiElement

class AndroidLintIntersectingDeepLinksInspection :
  AndroidLintInspectionBase(
    message("android.lint.inspections.intersecting.deep.links"),
    AppLinksValidDetector.INTERSECTING_DEEP_LINKS,
  ) {
  override fun getAllFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    incident: Incident,
    message: String,
    fixData: LintFix?,
    fixProviders: Array<out LintIdeQuickFixProvider>?,
    issue: Issue,
  ): Array<LintIdeQuickFix> {
    val quickFixes = super.getQuickFixes(startElement, endElement, incident).toMutableList()
    // If there is no location to jump to, don't add a quick-fix.
    val locationToJumpTo = incident.location.secondary ?: return quickFixes.toTypedArray()
    // Now insert the IDE-specific quick-fix.
    val fix = JumpToIntersectingDeepLinkFix(locationToJumpTo)
    fix.priority = PriorityAction.Priority.TOP
    quickFixes.add(0, fix)
    return quickFixes.toTypedArray()
  }
}
