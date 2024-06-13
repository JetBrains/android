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

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.JavaApiUsageInspection
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet

/**
 * This suppresses [JavaApiUsageInspection] in Android modules, where it is superseded by the NewApi
 * Lint check. The NewApi Lint check handles various Android-specific details such as runtime
 * version checks and API desugaring.
 */
class JavaApiUsageInspectionSuppressor : InspectionSuppressor {

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    // Note: "Since15" is the tool ID for JavaApiUsageInspection.
    return toolId == "Since15" && AndroidFacet.getInstance(element) != null
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}
