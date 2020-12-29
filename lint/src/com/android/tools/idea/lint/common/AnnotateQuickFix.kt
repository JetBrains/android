/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeParameter

class AnnotateQuickFix(
  private val displayName: String?,
  private val familyName: String?,
  private val annotationSource: String,
  private val replace: Boolean
) : LintIdeQuickFix {
  override fun getName(): String = displayName!!
  override fun getFamilyName(): String = familyName!!

  private fun findContainer(element: PsiElement): PsiElement? {
    return when (element.language) {
      JavaLanguage.INSTANCE -> findJavaAnnotationTarget(element)
      KotlinLanguage.INSTANCE -> findKotlinAnnotationTarget(element)
      else -> null
    }
  }

  override fun apply(element: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
    val language = element.language
    val container = findContainer(element) ?: return

    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) {
      return
    }

    when (language) {
      JavaLanguage.INSTANCE -> {
        val owner = container as PsiModifierListOwner
        val project = element.project
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val newAnnotation = factory.createAnnotationFromText(annotationSource, element)
        val annotationName = newAnnotation.qualifiedName ?: return
        val annotation = AnnotationUtil.findAnnotation(owner, annotationName)
        if (annotation != null && annotation.isPhysical && replace) {
          annotation.replace(newAnnotation)
        }
        else {
          val attributes = newAnnotation.parameterList.attributes
          AddAnnotationFix(annotationName, container, attributes).invoke(project, null, element.containingFile)
        }
      }
      KotlinLanguage.INSTANCE -> {
        val args = annotationSource.indexOf('(')
        val className = annotationSource.substring(0, if (args == -1) annotationSource.length else args).removePrefix("@")
        when (container) {
          is KtModifierListOwner -> {
            container.addAnnotation(
              FqName(className),
              if (args == -1) null else annotationSource.substring(args + 1, annotationSource.length - 1),
              whiteSpaceText = if (container.isNewLineNeededForAnnotation()) "\n" else " "
            )
          }
        }
      }
    }
  }

  override fun isApplicable(startElement: PsiElement, endElement: PsiElement, contextType: AndroidQuickfixContexts.ContextType): Boolean {
    return findContainer(startElement) != null
  }
}

fun PsiElement.isAnnotationTarget(): Boolean {
  return this is KtClassOrObject ||
         (this is KtFunction && this !is KtFunctionLiteral) ||
         (this is KtProperty && !isLocal && hasBackingField()) ||
         this is KtPropertyAccessor
}

fun KtElement.isNewLineNeededForAnnotation(): Boolean {
  return !(this is KtParameter ||
           this is KtTypeParameter ||
           this is KtPropertyAccessor)
}

fun findJavaAnnotationTarget(element: PsiElement?): PsiModifierListOwner? {
  val modifier = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner::class.java, true)
  return if (modifier !is PsiClassInitializer) {
    modifier
  }
  else {
    findJavaAnnotationTarget(modifier)
  }
}

private fun findKotlinAnnotationTarget(element: PsiElement) =
  PsiTreeUtil.findFirstParent(element, true) { it.isAnnotationTarget() }