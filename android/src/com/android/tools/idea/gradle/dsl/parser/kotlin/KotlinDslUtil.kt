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
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import java.math.BigDecimal

internal fun String.addQuotes(forExpression : Boolean) = if (forExpression) "\"$this\"" else "'$this'"

internal fun KtCallExpression.isBlockElement() : Boolean {
  return lambdaArguments.size == 1 && (valueArgumentList == null || (valueArgumentList as KtValueArgumentList).arguments.size < 2)
}

internal fun KtCallExpression.name() : String? {
  return getCallNameExpression()?.getReferencedName()
}

/**
 * Get the block name with the valid syntax in kotlin.
 * If the block was read from the KTS script, we use the `methodName` to create the block name. Otherwise, if we want to write
 * the block in the build file for the first time, we use maybeCreate because it tries to create the element only if it doesn't exist.
 */
internal fun getOriginalName(methodName : String?, blockName : String): String {
  return if (methodName != null) "$methodName(\"$blockName\")" else "maybeCreate(\"$blockName\")"
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
    is Int, is Boolean, is BigDecimal -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.toString())
    is RawText -> return KtPsiFactory(context.dslFile.project).createExpressionIfPossible(value.getText())
    else -> throw IncorrectOperationException("Expression '${value}' not supported.")
  }
}

internal fun findInjections(
  context: GradleDslSimpleExpression,
  psiElement: PsiElement,
  includeResolved: Boolean,
  injectionElement: PsiElement? = null
): MutableList<GradleReferenceInjection> {
  val noInjections = mutableListOf<GradleReferenceInjection>()
  val injectionPsiElement = injectionElement ?: psiElement
  when (psiElement) {
    // extra["PROPERTY_NAME"]
    is KtArrayAccessExpression -> {
      val arrayExpression = psiElement.arrayExpression ?: return noInjections
      val name = arrayExpression.text
      if (name == "extra") {
        val indices = psiElement.indexExpressions
        if (indices.size == 1) {
          val index = indices[0]
          if (index is KtStringTemplateExpression && !index.hasInterpolation()) {
            val entries = index.entries
            val entry = entries[0]
            val text = entry.text
            // TODO(xof): unquoting
            val element = context.resolveReference(text, true)
            return mutableListOf(GradleReferenceInjection(context, element, injectionPsiElement, text))
          }
        }
      }
      return noInjections
    }
    // "foo bar", "foo $bar", "foo ${extra["PROPERTY_NAME"]}"
    is KtStringTemplateExpression -> {
      if (!psiElement.hasInterpolation()) return noInjections
      return psiElement.entries
        .flatMap { entry -> when(entry) {
          // any constant portion of a KtStringTemplateExpression
          is KtLiteralStringTemplateEntry -> noInjections
          // TODO(xof): implement variable lookup (e.g. $bar)
          is KtSimpleNameStringTemplateEntry -> noInjections
          // long-form interpolation ${...} -- compute injections for the contained expression
          is KtBlockStringTemplateEntry -> entry.expression?.let { findInjections(context, it, includeResolved, entry) } ?: noInjections
          else -> noInjections
        }}
        .toMutableList()
    }
    else -> return noInjections
  }
}
