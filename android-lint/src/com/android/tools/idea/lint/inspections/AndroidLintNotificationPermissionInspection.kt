/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.lint.inspections.AndroidLintMissingPermissionInspection.AddPermissionFix
import com.android.tools.lint.checks.NotificationPermissionDetector
import com.android.tools.lint.checks.PermissionDetector
import com.android.tools.lint.detector.api.LintFix
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet

class AndroidLintNotificationPermissionInspection :
  AndroidLintInspectionBase(
    message("android.lint.inspections.notification.permission"),
    NotificationPermissionDetector.ISSUE
  ) {
  override fun getQuickFixes(
    startElement: PsiElement,
    endElement: PsiElement,
    message: String,
    quickfixData: LintFix?
  ): Array<LintIdeQuickFix> {
    if (quickfixData is LintFix.DataMap) {
      val names = quickfixData.getStringList(PermissionDetector.KEY_MISSING_PERMISSIONS)
      if (names != null) {
        val facet = AndroidFacet.getInstance(startElement)
        if (facet != null) {
          val fixes: MutableList<LintIdeQuickFix> = ArrayList(names.size)
          for (name in names) {
            fixes.add(AddPermissionFix(facet, name, Int.MAX_VALUE))
          }
          return fixes.toTypedArray()
        }
      }
    }
    return super.getQuickFixes(startElement, endElement, message, quickfixData)
  }
}
