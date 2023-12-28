/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.lang.typedef

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.parentOfType
import com.intellij.psi.PsiCall
import com.intellij.psi.PsiExpressionList
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Decorates, reprioritizes, and possibly adds named constants from Android typedef annotations
 * for code completion on a [PsiReferenceExpression](argument).
 *
 * See also [IntDef](https://developer.android.com/reference/androidx/annotation/IntDef),
 * [LongDef](https://developer.android.com/reference/androidx/annotation/LongDef), and
 * [StringDef](https://developer.android.com/reference/androidx/annotation/StringDef) documentation.
 */
class JavaTypeDefCompletionContributor : TypeDefCompletionContributor() {
  /** This looks for a place where we're completing an argument to a method or constructor call. */
  override val elementPattern: ElementPattern<PsiElement> = psiElement().withParent(
    psiElement(PsiReferenceExpression::class.java).inside(
      psiElement(PsiExpressionList::class.java).withParent(PsiCall::class.java)
    )
  )

  override val insertHandler = object: TypeDefInsertHandler() {
    override fun bindToTarget(context: InsertionContext, target: PsiElement) {
      val expr = context.getParent() as? PsiReference ?: return
      val psiField = when (target) {
        is PsiField -> target
        is KtProperty -> target.toLightElements().firstOrNull() ?: return
        else -> return
      }
      expr.bindToElement(psiField)
      // Get the largest containing expression and shorten references within.
      context.getMaximalParentOfType<PsiReferenceExpression>()?.let {
        JavaCodeStyleManager.getInstance(context.project).shortenClassReferences(it)
      }
    }
  }


  override fun computeConstrainingTypeDef(position: PsiElement): TypeDef? {
    val arg = position.parentOfType<PsiReferenceExpression>() ?: return null
    val call = arg.parentOfType<PsiCall>() ?: return null
    val argIndex = call.argumentList?.expressions?.indexOf(arg) ?: return null
    return call.resolveMethod()?.getArgumentTypeDef(argIndex = argIndex)
  }
}
