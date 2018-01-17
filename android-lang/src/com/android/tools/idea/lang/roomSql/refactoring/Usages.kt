/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.lang.roomSql.refactoring

import com.android.tools.idea.lang.roomSql.COMMENTS
import com.android.tools.idea.lang.roomSql.IDENTIFIERS
import com.android.tools.idea.lang.roomSql.STRING_LITERALS
import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomNameElement
import com.android.tools.idea.lang.roomSql.resolution.RoomSchema
import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.android.tools.idea.lang.roomSql.resolution.SqlDefinition
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor

/**
 * No-op [FindUsagesProvider] that provides the right [WordsScanner] for SQL.
 *
 * Having one for the language enables the "find usages" UI, which is what we want. Currently there are no PSI references pointing to
 * SQL elements, so this provider doesn't have to do anything, it never actually gets invoked. The only references are from SQL to Java,
 * but that is handled by java usages providers.
 *
 * We cannot inherit from [EmptyFindUsagesProvider], because [com.intellij.find.actions.FindUsagesInFileAction] has an explicit check
 * that disables the action if the provider is an instance of [EmptyFindUsagesProvider].
 */
class RoomFindUsagesProvider : FindUsagesProvider by EmptyFindUsagesProvider() {
  override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(RoomSqlLexer(), IDENTIFIERS, COMMENTS, STRING_LITERALS)
}

/**
 * `referencesSearch` that checks the word index for the right words (in case a table/column name is not the same as class name).
 */
class RoomReferenceSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
  private fun getSchema(element: PsiElement) = ReadAction.compute<RoomSchema?, Nothing> {
    RoomSchemaManager.getInstance(element.project)?.getSchema(element.containingFile)
  }

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>) {
    val (word, referenceTarget) = ReadAction.compute<Pair<String, PsiElement>?, Throwable> {
      getNameDefinition(queryParameters.elementToSearch)?.let(this::chooseWordAndElement)
    } ?: return

    // Note: QueryExecutorBase is a strange API: the naive way to implement it is to somehow find the references and feed them to the
    // consumer. The "proper" way is to get the optimizer and schedule an index search for references to a given element that include a
    // given text. Scheduled searches get merged and run later. We use the latter API, but:
    //   - We search for the right word (as calculate above).
    //   - We use `scopeDeterminedByUser`, not `effectiveScope`. Effective scope for a private field contains only the file in which it's
    //     defined, which is not how Room works.
    //   - We look for references to the right element: if a table/column name is overridden using annotations, the PSI references may not
    //     point to the class/field itself, but we still want to show these references in "find usages".
    queryParameters.optimizer.searchWord(word, queryParameters.scopeDeterminedByUser, false, referenceTarget)
  }

  private fun chooseWordAndElement(definition: SqlDefinition): Pair<String, PsiElement>? {
    val name = definition.name ?: return null

    val word = when {
      RoomNameElementManipulator.needsQuoting(name) -> {
        // We need to figure out how a reference to this element looks like in the IdIndex. We find the first "word" in the quoted name and
        // look for it in the index, as any reference for this table will include this word in its text.
        val processor = CommonProcessors.FindFirstProcessor<WordOccurrence>()
        RoomFindUsagesProvider().wordsScanner.processWords(RoomNameElementManipulator.getValidName(name), processor)
        processor.foundValue?.let { it.baseText.substring(it.start, it.end) } ?: name
      }
      else -> name
    }

    return Pair(word, definition.resolveTo)
  }

  private fun getNameDefinition(element: PsiElement): SqlDefinition? {
    return when (element) {
      is PsiClass -> getSchema(element)?.findEntity(element) ?: return null
      is PsiField -> getSchema(element)
          ?.findEntity(element.containingClass ?: return null)
          ?.columns
          ?.find { it.psiField.element == element }
          ?: return null
      else -> return null
    }
  }
}

private val SQL_USAGE_TYPE = UsageType("Referenced in SQL query")

/**
 * [UsageTypeProvider] that labels references from SQL with the right description.
 *
 * @see SQL_USAGE_TYPE
 */
class RoomUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement?) = if (element is RoomNameElement) SQL_USAGE_TYPE else null
}
