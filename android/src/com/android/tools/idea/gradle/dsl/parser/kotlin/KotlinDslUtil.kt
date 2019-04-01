/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.kotlin

import com.android.tools.idea.gradle.dsl.api.ext.RawText
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

internal fun String.addQuotes(forExpression : Boolean) = if (forExpression) "\"$this\"" else "'$this'"

internal fun KtCallExpression.isBlockElement() : Boolean {
  return lambdaArguments.size == 1 && valueArgumentList == null
}

internal fun KtCallExpression.name() : String? {
  return getCallNameExpression()?.getReferencedName()
}

@Throws(IncorrectOperationException::class)
internal fun createLiteral(context : GradleDslElement, value : Any) : PsiElement? {
   when (value) {
    is String ->  {
      var valueText : String?
      if (StringUtil.isQuotedString(value)) {
        val unquoted = StringUtil.unquoteString(value)
        valueText = StringUtil.escapeStringCharacters(unquoted).addQuotes(true)
      }
      else {
        valueText = StringUtil.escapeStringCharacters(value).addQuotes(false)
      }
      return KtPsiFactory(context.dslFile.project).createExpression(valueText)
    }
    is Int, Boolean -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.toString())
    is RawText -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.getText())
    else -> throw IncorrectOperationException("Expression '${value}' not supported.")
  }
}

internal fun findInjections(
  context : GradleDslSimpleExpression, psiElement: PsiElement, includeResolved : Boolean) : MutableList<GradleReferenceInjection> {
  return mutableListOf()
}
