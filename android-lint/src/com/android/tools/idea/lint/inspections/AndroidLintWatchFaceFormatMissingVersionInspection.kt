/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.idea.lint.quickFixes.AddWatchFaceFormatVersionPropertyQuickFix
import com.android.tools.lint.checks.WatchFaceFormatVersionDetector
import com.android.tools.lint.detector.api.Incident
import com.intellij.psi.PsiElement

class AndroidLintWatchFaceFormatMissingVersionInspection :
  AndroidLintInspectionBase(
    message("android.lint.inspections.watch.face.format.version.missing"),
    WatchFaceFormatVersionDetector.MISSING_VERSION_ISSUE,
  ) {

  override fun getQuickFixes(startElement: PsiElement, endElement: PsiElement, incident: Incident) =
    super.getQuickFixes(startElement, endElement, incident) +
      AddWatchFaceFormatVersionPropertyQuickFix()
}
