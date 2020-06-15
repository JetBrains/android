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
package com.android.tools.idea.lint

import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.util.mapAndroidxName
import com.android.tools.lint.checks.ObjectAnimatorDetector.KEEP_ANNOTATION
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction

class AddKeepAnnotationFix : LintIdeQuickFix {
  override fun apply(startElement: PsiElement,
                     endElement: PsiElement,
                     context: AndroidQuickfixContexts.Context) {
    when (startElement.language) {
      JavaLanguage.INSTANCE -> applyJava(startElement)
      KotlinLanguage.INSTANCE -> applyKotlin(startElement)
    }
  }

  private fun applyJava(element: PsiElement) {
    val container = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner::class.java) ?: return
    val project = element.project
    val annotationName = ModuleUtilCore.findModuleForPsiElement(element).mapAndroidxName(KEEP_ANNOTATION)
    AddAnnotationFix(annotationName, container).invoke(project, null, container.containingFile)
  }

  private fun applyKotlin(element: PsiElement) {
    val method = PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java) ?: return
    if (!FileModificationService.getInstance().preparePsiElementForWrite(method)) {
      return
    }
    val annotationName = FqName(ModuleUtilCore.findModuleForPsiElement(element).mapAndroidxName(KEEP_ANNOTATION))
    method.addAnnotation(annotationName, null, whiteSpaceText = " ")
  }

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean = true

  override fun getName(): String = "Annotate with @Keep"
}
