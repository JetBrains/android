/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.intellij.codeInspection.HTMLComposer
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension
import com.intellij.codeInspection.lang.HTMLComposerExtension
import com.intellij.codeInspection.lang.InspectionExtensionsFactory
import com.intellij.codeInspection.lang.RefManagerExtension
import com.intellij.codeInspection.reference.RefManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class LintInspectionExtensionsFactory : InspectionExtensionsFactory() {
  override fun createGlobalInspectionContextExtension(): GlobalInspectionContextExtension<*> =
    LintGlobalInspectionContext()
  override fun createRefManagerExtension(refManager: RefManager): RefManagerExtension<*>? = null
  override fun createHTMLComposerExtension(composer: HTMLComposer): HTMLComposerExtension<*>? = null
  override fun isToCheckMember(element: PsiElement, id: String): Boolean = true
  override fun getSuppressedInspectionIdsIn(element: PsiElement): String? = null
  override fun isProjectConfiguredToRunInspections(project: Project, online: Boolean): Boolean =
    true
}
