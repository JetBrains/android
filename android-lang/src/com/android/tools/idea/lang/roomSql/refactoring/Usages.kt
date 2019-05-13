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
package com.android.tools.idea.lang.roomSql.refactoring

import com.android.tools.idea.lang.roomSql.COMMENTS
import com.android.tools.idea.lang.roomSql.IDENTIFIERS
import com.android.tools.idea.lang.roomSql.NotRenamableElement
import com.android.tools.idea.lang.roomSql.RoomAnnotations
import com.android.tools.idea.lang.roomSql.STRING_LITERALS
import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomNameElement
import com.android.tools.idea.lang.roomSql.resolution.PsiElementForFakeColumn
import com.android.tools.idea.lang.roomSql.resolution.RoomSchema
import com.android.tools.idea.lang.roomSql.resolution.RoomSchemaManager
import com.android.tools.idea.lang.roomSql.resolution.SqlColumn
import com.android.tools.idea.lang.roomSql.resolution.SqlDefinition
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.impl.id.ScanningIdIndexer
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

class RoomIdIndexer : ScanningIdIndexer() {
  override fun createScanner(): WordsScanner = RoomFindUsagesProvider().wordsScanner
  override fun getVersion(): Int = 0
}

/**
 * `referencesSearch` that checks the word index for the right words (in case a table/column name is not the same as class name).
 */
class RoomReferenceSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
  private fun getSchema(element: PsiElement) = ReadAction.compute<RoomSchema?, Nothing> {
    if (element.containingFile != null) {
      RoomSchemaManager.getInstance(element.project)?.getSchema(element.containingFile)
    } else {
      null
    }
  }

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    var element = queryParameters.elementToSearch
    if (element is NotRenamableElement) {
      element = element.delegate
    }

    val (words, referenceTarget) = runReadAction {
      // Return early if possible: this method is called by various inspections on all kinds of PSI elements, in most cases we don't have to
      // do anything which means we don't block a FJ thread by building a Room schema.
      val definesSqlSchema = when (element) {
        is PsiElementForFakeColumn -> true
        is PsiClass -> element.definesSqlTable()
        is PsiField -> {
          val psiClass = element.containingClass
          // A subclass can be annotated with `@Entity`, making fields into SQL column definitions.
          !(psiClass == null || psiClass.hasModifier(JvmModifier.FINAL) && !psiClass.definesSqlTable())
        }
        else -> false
      }

      if (!definesSqlSchema) {
        return@runReadAction null
      }

      getNameDefinition(element)?.let(this::chooseWordsAndElement)
    } ?: return

    // Note: QueryExecutorBase is a strange API: the naive way to implement it is to somehow find the references and feed them to the
    // consumer. The "proper" way is to get the optimizer and schedule an index search for references to a given element that include a
    // given text. Scheduled searches get merged and run later. We use the latter API, but:
    //   - We search for the right word (as calculate above).
    //   - We use `scopeDeterminedByUser`, not `effectiveScope`. Effective scope for a private field contains only the file in which it's
    //     defined, which is not how Room works.
    //   - We look for references to the right element: if a table/column name is overridden using annotations, the PSI references may not
    //     point to the class/field itself, but we still want to show these references in "find usages".
    for (word in words) {
      queryParameters.optimizer.searchWord(word, queryParameters.scopeDeterminedByUser, false, referenceTarget)
    }
  }

  private fun PsiClass.definesSqlTable(): Boolean {
    return hasAnnotation(RoomAnnotations.ENTITY.oldName()) ||
           hasAnnotation(RoomAnnotations.ENTITY.newName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.oldName()) ||
           hasAnnotation(RoomAnnotations.DATABASE_VIEW.newName())
  }

  /**
   * Returns set of words and element for given [SqlDefinition] to check during search usage process
   *
   * Some columns can be used by [SqlColumn.alternativeNames] and in search we need to find usages of all of them
   */
  private fun chooseWordsAndElement(definition: SqlDefinition): Pair<List<String>, PsiElement>? {
    val names = ArrayList<String>()
    if (definition.name != null) names.add(definition.name!!)
    if (definition is SqlColumn) names.addAll(definition.alternativeNames)

    if (names.isEmpty()) return null

    val words = names.map { name ->
      when {
        RoomNameElementManipulator.needsQuoting(name) -> {
          // We need to figure out how a reference to this element looks like in the IdIndex.
          // We find the first "word" in the quoted name and look for it in the index,
          // as any reference for this table will include this word in its text.
          val processor = CommonProcessors.FindFirstProcessor<WordOccurrence>()
          RoomFindUsagesProvider().wordsScanner.processWords(RoomNameElementManipulator.getValidName(name), processor)
          processor.foundValue?.let { it.baseText.substring(it.start, it.end) } ?: name
        }
        else -> name
      }
    }

    return Pair(words, definition.resolveTo)
  }

  private fun getNameDefinition(element: PsiElement): SqlDefinition? {
    return when (element) {
      is PsiClass -> {
        getSchema(element)?.findTable(element)
      }
      is PsiField -> {
        getSchema(element)
          ?.findTable(element.containingClass ?: return null)
          ?.columns
          ?.find { it.definingElement == element }
      }
      is PsiElementForFakeColumn -> {
        getSchema(element.tablePsiElement)
          ?.findTable(element.tablePsiElement)
          ?.columns
          ?.find { PsiManager.getInstance(element.project).areElementsEquivalent(it.definingElement, element) }
      }
      else -> null
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
