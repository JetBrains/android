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
package com.android.tools.idea.preview.quickfixes

import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.preview.PreviewBundle.message
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtModifierListOwner

/**
 * Quick fix that replaces a @Preview that is not [withAnnotationFqn] with [withAnnotationFqn]. This
 * quick fix will also remove the previous @Preview import directive if it is not used elsewhere in
 * the file.
 *
 * This quick fix supports both Kotlin and Java annotations.
 */
class ReplacePreviewAnnotationFix(
  invalidAnnotation: PsiElement,
  private val withAnnotationFqn: String,
) : LocalQuickFixOnPsiElement(invalidAnnotation) {
  override fun getFamilyName() = message("inspection.quick.fix.family")

  override fun getText() = message("inspection.quick.fix.replace.annotation", withAnnotationFqn)

  private var invalidPreviewImportDirectiveFqName: String? = null

  override fun isAvailable(
    project: Project,
    file: PsiFile,
    startElement: PsiElement,
    endElement: PsiElement,
  ): Boolean {
    return when (file.language) {
      KotlinLanguage.INSTANCE -> {
        val invalidPreviewAnnotation = startElement as? KtAnnotationEntry ?: return false
        invalidPreviewImportDirectiveFqName =
          invalidPreviewAnnotation.getQualifiedName()?.takeIf { fqName ->
            PsiTreeUtil.findChildrenOfType(file, KtAnnotationEntry::class.java).none {
              if (it == invalidPreviewAnnotation) return@none false
              it.shortName != null && it.fqNameMatches(fqName)
            }
          } ?: ""
        true
      }
      else -> super.isAvailable(project, file, startElement, endElement)
    }
  }

  override fun invoke(
    project: Project,
    file: PsiFile,
    startElement: PsiElement,
    endElement: PsiElement,
  ) {
    when (file.language) {
      KotlinLanguage.INSTANCE -> handleKotlin(file, startElement)
      JavaLanguage.INSTANCE -> handleJava(startElement)
    }
  }

  private fun handleJava(element: PsiElement) {
    val invalidPreviewAnnotation = element as? PsiAnnotation ?: return
    val invalidPreviewAnnotationFqn = invalidPreviewAnnotation.qualifiedName ?: ""
    val parent = invalidPreviewAnnotation.parentOfType<PsiModifierListOwner>() ?: return
    val annotationAttributes = invalidPreviewAnnotation.parameterList.attributes

    val delegateFix =
      AddAnnotationPsiFix(
        withAnnotationFqn,
        parent,
        annotationAttributes,
        invalidPreviewAnnotationFqn,
      )
    delegateFix.applyFix()
  }

  private fun handleKotlin(file: PsiFile, element: PsiElement) {
    val invalidPreviewAnnotation = element as? KtAnnotationEntry ?: return
    val parent = invalidPreviewAnnotation.parentOfType<KtModifierListOwner>() ?: return
    val innerText =
      invalidPreviewAnnotation.valueArgumentList?.text?.let {
        // remove beginning and end parentheses
        it.substring(1, it.length - 1)
      }

    invalidPreviewAnnotation.delete()

    // Delete import directives for the invalid preview annotation.
    if (invalidPreviewImportDirectiveFqName != null) {
      PsiTreeUtil.findChildrenOfType(file, KtImportDirective::class.java)
        .filter { it.importedFqName?.asString() == invalidPreviewImportDirectiveFqName }
        .forEach { it.delete() }
    }

    parent.addAnnotation(
      ClassId.fromString(withAnnotationFqn),
      innerText,
      searchForExistingEntry = false,
    )
    ShortenReferencesFacility.getInstance().shorten(parent)
  }
}
