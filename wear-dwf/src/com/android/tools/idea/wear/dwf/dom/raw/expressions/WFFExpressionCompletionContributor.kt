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

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources.DAYS_TOKEN
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources.HOURS_TOKEN
import com.android.tools.idea.wear.dwf.WFFConstants.Functions
import com.android.tools.idea.wear.dwf.dom.raw.CurrentWFFVersionService
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.FUNCTION_ID
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.LITERAL_EXPR
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionTypes.OPEN_BRACKET
import com.android.tools.idea.wear.dwf.dom.raw.insertBracketsAroundIfNeeded
import com.android.tools.wear.wff.WFFVersion
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.not
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
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

  private val dataSourcesProvider =
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

        val availablePatternedDataSource =
          if (wffVersion == null) DataSources.ALL_PATTERNS
          else DataSources.ALL_AVAILABLE_PATTERNS_BY_VERSION.getValue(wffVersion)

        val prefix = resultSet.prefixMatcher.prefix
        val lookupElementsWithUserInput =
          availablePatternedDataSource.getLookupElementsWithUserTokenInput(prefix)
        if (lookupElementsWithUserInput != null) {
          resultSet.addAllElements(lookupElementsWithUserInput)
          return
        }

        val availableStaticDataSources =
          getAvailableStaticDataSources(wffVersion, parameters.position)

        resultSet.addAllElements(
          availableStaticDataSources.map { createDataSourceLookupElement(it.id) }
        )

        resultSet.addAllElements(
          availablePatternedDataSource.map {
            createDataSourceLookupElement(it.lookupString).withInsertHandler { context, lookupItem
              ->
              insertBracketsAroundIfNeeded(context, lookupItem)
              moveCursorToCursorTokenAndRemoveIt(context, lookupItem, it)
            }
          }
        )
      }
    }

  /**
   * Returns all the static data sources for a given [WFFVersion] and [PsiElement].
   *
   * If the version is not null, the list will only include static data sources that are available
   * for that version.
   *
   * If the given [PsiElement] is located under a `<Complication>` tag, it will include any
   * complication data sources that are compatible with that complication's type.
   *
   * @see <a
   *   href="https://developer.android.com/reference/wear-os/wff/complication/complication">Complication</a>
   */
  private fun getAvailableStaticDataSources(
    wffVersion: WFFVersion?,
    element: PsiElement,
  ): List<StaticDataSource> {
    val allStaticDataSources =
      if (wffVersion == null) DataSources.ALL_STATIC
      else DataSources.ALL_AVAILABLE_STATIC_BY_VERSION.getValue(wffVersion)

    val allStaticDataSourcesWithoutComplications =
      allStaticDataSources - DataSources.COMPLICATION_ALL
    val complicationParentTag = getParentComplicationTag(element)
    val complicationsForType =
      DataSources.COMPLICATION_BY_TYPE[
          complicationParentTag?.getAttribute(SdkConstants.ATTR_TYPE)?.value]
    if (complicationsForType == null) {
      return allStaticDataSourcesWithoutComplications
    }
    return allStaticDataSourcesWithoutComplications + complicationsForType
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

    extend(
      CompletionType.BASIC,
      psiElement().withAncestor(3, psiElement(LITERAL_EXPR)),
      dataSourcesProvider,
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

  /**
   * Replaces patterned data sources with user's input for given tokens, if the user has specified a
   * value for the token. The user's token input is found in the given [prefix]. If the user has not
   * specified a token value, then return `null`.
   *
   * For example, if the prefix is `WEATHER.DAYS.5.`, the user's input will be `5` against, the
   * pattern `WEATHER.DAYS.<days>`. The lookup elements will replace all `<days>` string in the
   * lookup strings with `5`.
   *
   * If the prefix is `WEATHER.`, then the prefix will not match any patterns, so `null` will be
   * returned.
   */
  private fun List<PatternedDataSource>.getLookupElementsWithUserTokenInput(
    prefix: String
  ): List<LookupElementBuilder>? {
    val patternsToTokens =
      mapOf(
        DataSources.WEATHER_DAYS_PATTERN to DAYS_TOKEN,
        DataSources.WEATHER_HOURS_PATTERN to HOURS_TOKEN,
      )

    for ((pattern, token) in patternsToTokens) {
      val userTokenInput = pattern.find(prefix)?.groups?.get(1)
      if (userTokenInput == null) continue
      return map {
        createDataSourceLookupElement(it.lookupString.replace(token, userTokenInput.value))
      }
    }
    return null
  }

  private fun createDataSourceLookupElement(lookupString: String) =
    LookupElementBuilder.create(lookupString)
      .withLookupStrings(listOf(lookupString, "[$lookupString]"))
      .withPresentableText("[$lookupString]")
      .insertBracketsAroundIfNeeded()

  /**
   * Finds the [PatternedDataSource.lookupCursorToken] in the `lookupItem`, moves the cursor to that
   * location and then removes the token.
   */
  private fun moveCursorToCursorTokenAndRemoveIt(
    context: InsertionContext,
    lookupItem: LookupElement,
    dataSource: PatternedDataSource,
  ) {
    val tokenOffsetInLookupString = lookupItem.lookupString.indexOf(dataSource.lookupCursorToken)
    if (tokenOffsetInLookupString < 0) return
    val cursorOffset = context.startOffset + tokenOffsetInLookupString + 1

    context.editor.caretModel.moveToOffset(cursorOffset)
    context.document.replaceString(
      cursorOffset,
      cursorOffset + dataSource.lookupCursorToken.length,
      "",
    )
  }
}
