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
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference.ShorteningMode
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Decorates, reprioritizes, and possibly adds named constants from Android typedef annotations
 * for a code completion on a [KtValueArgument].
 *
 * See also [IntDef](https://developer.android.com/reference/androidx/annotation/IntDef),
 * [LongDef](https://developer.android.com/reference/androidx/annotation/LongDef), and
 * [StringDef](https://developer.android.com/reference/androidx/annotation/StringDef) documentation.
 */
class KotlinTypeDefCompletionContributor : TypeDefCompletionContributor() {
  /** This looks for a place where we're completing an argument to a method or constructor call. */
  override val elementPattern: ElementPattern<PsiElement> =
    PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(KtValueArgument::class.java))

  override val insertHandler = object : TypeDefInsertHandler() {
    override fun bindToTarget(context: InsertionContext, target: PsiElement) {
      val expr = context.getParent() as? KtReferenceExpression ?: return
      (expr.mainReference as? KtSimpleNameReference)?.bindToElement(target, ShorteningMode.FORCED_SHORTENING)
    }
  }

  override fun computeConstrainingTypeDef(position: PsiElement) = position.parentOfType<KtValueArgument>()?.getTypeDef()

  /**
   * Returns typedef values for the first typedef annotation encountered, or `null` if there is no
   * typedef annotation for this [KtValueArgument].
   */
  private fun KtValueArgument.getTypeDef(): TypeDef? {
    if (this is KtLambdaArgument) return null

    val calleeElement =
      parentOfType<KtCallElement>()?.calleeExpression
        ?.let { if (it is KtConstructorCalleeExpression) it.constructorReferenceExpression else it }
        ?.mainReference?.resolve()?.navigationElement
      ?: return null

    val index = (parent as KtValueArgumentList).arguments.indexOf(this)
    val name = getArgumentName()?.asName?.asString()
    return calleeElement.getArgumentTypeDef(name, index)
  }
}
