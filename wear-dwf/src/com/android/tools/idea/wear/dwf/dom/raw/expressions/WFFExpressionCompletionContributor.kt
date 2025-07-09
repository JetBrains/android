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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wear.dwf.WFFConstants.Functions
import com.android.tools.idea.wear.dwf.dom.raw.CurrentWFFVersionService
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.FUNCTION_ID
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.LITERAL_EXPR
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.OPEN_BRACKET
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.not
import com.intellij.patterns.StandardPatterns.or
import com.intellij.util.ProcessingContext

/** [CompletionContributor] that adds completion options to [WFFExpressionLanguage] elements. */
class WFFExpressionCompletionContributor : CompletionContributor() {

  private val functionIdsProvider =
    object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet,
      ) {
        val module = parameters.position.getModuleSystem()?.module
        val wffVersion =
          module?.let {
            CurrentWFFVersionService.getInstance().getCurrentWFFVersion(module)?.wffVersion
          }
        val availableFunctions =
          if (wffVersion == null) Functions.ALL
          else Functions.ALL_AVAILABLE_FUNCTIONS_BY_VERSION.getValue(wffVersion)

        availableFunctions.forEach {
          resultSet.addElement(
            LookupElementBuilder.create(it.id)
              .withPresentableText("${it.id}()")
              .insertParenthesisAfterIfNeeded()
          )
        }
      }
    }

  init {
    extend(
      CompletionType.BASIC,
      and(
        or(
          psiElement().withAncestor(3, psiElement(LITERAL_EXPR)),
          psiElement().withParent(psiElement(FUNCTION_ID)),
        ),
        not(psiElement().afterLeaf(psiElement(OPEN_BRACKET))),
      ),
      functionIdsProvider,
    )
  }

  fun LookupElementBuilder.insertParenthesisAfterIfNeeded() =
    withInsertHandler { context, lookupItem ->
      val areParenthesisNeeded =
        context.document.textLength < context.tailOffset + 1 ||
          context.document.getText(TextRange(context.tailOffset, context.tailOffset + 1)) != "("

      if (areParenthesisNeeded) {
        context.document.replaceString(
          context.startOffset,
          context.tailOffset,
          "${lookupItem.lookupString}()",
        )
      }
      // move the cursor to after the open parenthesis
      val openParenthesisOffset =
        if (areParenthesisNeeded) context.tailOffset - 1 else context.tailOffset + 1
      context.editor.caretModel.moveToOffset(openParenthesisOffset)
    }
}
