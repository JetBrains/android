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
package com.android.tools.idea.lang.roomSql

import com.android.tools.idea.lang.roomSql.parser.RoomSqlLexer
import com.android.tools.idea.lang.roomSql.psi.RoomPsiTypes
import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.EmptyFindUsagesProvider
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.TokenSet
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.ArrayUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor

/**
 * A [PsiReference] pointing from the table name in SQL to the Java PSI element defining the table name.
 *
 * @see Entity.tableNameElement
 */
class RoomTablePsiReference(val tableName: RoomTableName) : PsiReferenceBase.Poly<RoomTableName>(tableName) {

  private val schema: RoomSchema?
    get() = ModuleUtil.findModuleForPsiElement(tableName)?.let(RoomSchemaManager.Companion::getInstance)?.getSchema()

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      schema
          ?.entities
          ?.asSequence()
          ?.filter { it.tableName.equals(tableName.nameAsString, ignoreCase = !tableName.isQuoted) }
          ?.map { it.tableNameElement }
          ?.toList()
          .orEmpty())

  override fun getVariants(): Array<Any> =
      schema
          ?.entities
          ?.map {
            LookupElementBuilder.create(it.psiClass, RoomSqlLexer.getValidName(it.tableName))
                .withTypeText(it.psiClass.qualifiedName, true)
                .withCaseSensitivity(RoomSqlLexer.needsQuoting(it.tableName))
          }
          ?.let { it as? List<Any> }
          ?.toTypedArray()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY
}

/**
 * [ElementManipulator] that inserts the new table name (possibly quoted) into the PSI.
 *
 * It also specifies the range in element, which for quoted table names does not include the quotes. This fixes the inline renamer,
 * because SQL remains valid when renaming quoted names.
 */
class RoomTableNameManipulator : AbstractElementManipulator<RoomTableName>() {
  override fun handleContentChange(element: RoomTableName, range: TextRange, newContent: String?): RoomTableName {
    if (newContent.isNullOrEmpty()) {
      return element
    }

    val newName = RoomSqlPsiFacade.getInstance(element.project)?.createTableName(newContent!!) ?: return element
    return element.replace(newName) as RoomTableName
  }

  override fun getRangeInElement(tableName: RoomTableName): TextRange =
      if (tableName.identifier != null) TextRange(0, tableName.textLength) else TextRange(1, tableName.textLength - 1)
}

private val IDENTIFIERS = TokenSet.create(RoomPsiTypes.IDENTIFIER, RoomPsiTypes.BRACKET_LITERAL)
private val COMMENTS = TokenSet.create(RoomPsiTypes.COMMENT, RoomPsiTypes.LINE_COMMENT)
private val LITERALS = TokenSet.create(RoomPsiTypes.STRING_LITERAL)

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
  override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(RoomSqlLexer(), IDENTIFIERS, COMMENTS, LITERALS)
}

/**
 * `referencesSearch` that checks the word index for the right words (in case table name is not the same as class name).
 */
class RoomReferenceSearchExecutor : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>) {
    val psiClass = queryParameters.elementToSearch as? PsiClass ?: return

    val schema: RoomSchema = ReadAction.compute<RoomSchema?, Nothing> {
      ModuleUtil.findModuleForPsiElement(psiClass)
          ?.let(RoomSchemaManager.Companion::getInstance)
          ?.getSchema()
    } ?: return

    val entity = schema.entities.find { it.psiClass == psiClass } ?: return

    // We need to figure out how a reference to this table name looks like in the IdIndex. We find the first "word" in the quoted name and
    // look for it in the index, as any RoomTableName for this table will include this word in its text.
    val word: String =
        if (entity.psiClass == entity.tableNameElement)
          // The table name is not overridden, which means it's a valid Java name and therefore a valid IdIndex "word".
          entity.tableName
        else {
          val processor = CommonProcessors.FindFirstProcessor<WordOccurrence>()
          RoomFindUsagesProvider().wordsScanner.processWords(RoomSqlLexer.getValidName(entity.tableName), processor)
          processor.foundValue?.let { it.baseText.substring(it.start, it.end) } ?: entity.tableName
        }


    // Note: QueryExecutorBase is a strange API: the naive way to implement it is to somehow find the references and feed them to the
    // consumer. The "proper" way is to get the optimizer and schedule an index search for references to a given element that include a
    // given text. Scheduled searches get merged and run later.
    queryParameters.optimizer.searchWord(word, queryParameters.effectiveSearchScope, false, entity.tableNameElement)
  }
}

private val SQL_USAGE_TYPE = UsageType("Referenced in SQL query")

/**
 * [UsageTypeProvider] that labels references from SQL with the right description.
 *
 * @see SQL_USAGE_TYPE
 */
class RoomUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement?) =
      if (element is RoomTableName) SQL_USAGE_TYPE else null
}
