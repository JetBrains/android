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
import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomNameElement
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
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.ArrayUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor


/**
 * A [PsiReference] pointing from the column name in SQL to the Java PSI element defining the column name.
 *
 * @see Column.nameElement
 */
class RoomColumnPsiReference(private val columnName: RoomColumnName) : PsiReferenceBase.Poly<RoomColumnName>(columnName) {

  private val schema: RoomSchema?
    get() = ModuleUtil.findModuleForPsiElement(columnName)?.let(RoomSchemaManager.Companion::getInstance)?.getSchema()

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      schema
          ?.entities
          ?.asSequence()
          ?.flatMap { it.columns.asSequence() }
          ?.filter { it.name.equals(columnName.nameAsString, ignoreCase = true) }
          ?.map { it.nameElement }
          ?.toList()
          .orEmpty())

  override fun getVariants(): Array<Any> =
      schema
          ?.entities
          ?.flatMap { (psiClass, _, _ , columns) ->
            columns.map { (psiField, columnName) ->
              LookupElementBuilder.create(psiField, RoomSqlLexer.getValidName(columnName))
                  .withTypeText(psiClass.qualifiedName, true)
                  // Columns that come from Java fields will most likely use camelCase, starting with a lower-case letter. By default code
                  // completion is configured to only check the case of first letter (see Settings), so if the user types `isv` we will
                  // suggest e.g. `isValid`. Keeping this flag set to true means that the inserted string is exactly the same as the field
                  // name (`isValid`) which seems a good UX. See the below for why table name completion is configured differently.
                  //
                  // The interactions between this flag and the user-level setting are non-obvious, so consider all cases before changing.
                  .withCaseSensitivity(true)
            }
          }
          ?.let { it as? List<Any> }
          ?.toTypedArray()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY
}


/**
 * A [PsiReference] pointing from the table name in SQL to the Java PSI element defining the table name.
 *
 * @see Entity.nameElement
 */
class RoomTablePsiReference(private val tableName: RoomTableName) : PsiReferenceBase.Poly<RoomTableName>(tableName) {

  private val schema: RoomSchema?
    get() = ModuleUtil.findModuleForPsiElement(tableName)?.let(RoomSchemaManager.Companion::getInstance)?.getSchema()

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      schema
          ?.entities
          ?.asSequence()
          ?.filter { it.name.equals(tableName.nameAsString, ignoreCase = true) }
          ?.map { it.nameElement }
          ?.toList()
          .orEmpty())

  override fun getVariants(): Array<Any> =
      schema
          ?.entities
          ?.map { (psiClass, tableName) ->
            LookupElementBuilder.create(psiClass, RoomSqlLexer.getValidName(tableName))
                .withTypeText(psiClass.qualifiedName, true)
                // Tables that come from Java classes will have the first letter in upper case and by default the IDE has code completion
                // configured to be case sensitive on the first letter (see Settings), so if the user types `b` we won't offer them neither
                // `Books` nor `books`. This is consistent with the Java editor, but probably not what most users want. Our own samples
                // use lower-case table names and it seems a better UX is to insert `books` if the user types `b`. Setting this flag to
                // false means that although we show `Books` in the UI, the actual inserted text is `books`, interpolating case from what
                // the user has typed.
                //
                // The interactions between this flag and the user-level setting are non-obvious, so consider all cases before changing.
                .withCaseSensitivity(false)
          }
          ?.let { it as? List<Any> }
          ?.toTypedArray()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY
}

/**
 * [ElementManipulator] that inserts the new name (possibly quoted) into the PSI.
 *
 * It also specifies the range in element, which for quoted names does not include the quotes. This fixes the inline renamer,
 * because SQL remains valid when renaming quoted names.
 */
class RoomNameElementManipulator : AbstractElementManipulator<RoomNameElement>() {
  override fun handleContentChange(element: RoomNameElement, range: TextRange, newContent: String?): RoomNameElement {
    if (newContent.isNullOrEmpty()) {
      return element
    }

    val newName = RoomSqlPsiFacade.getInstance(element.project)?.run {
      when (element.node.elementType) {
        RoomPsiTypes.TABLE_NAME -> createTableName(newContent!!)
        RoomPsiTypes.COLUMN_NAME -> createColumnName(newContent!!)
        else -> error("Don't know how to rename ${element.node.elementType}")
      }
    } ?: return element

    return element.replace(newName) as RoomNameElement
  }

  override fun getRangeInElement(nameElement: RoomNameElement): TextRange =
      if (nameElement.nameIsQuoted) TextRange(1, nameElement.textLength - 1) else TextRange(0, nameElement.textLength)
}

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
    ModuleUtil.findModuleForPsiElement(element)
        ?.let(RoomSchemaManager.Companion::getInstance)
        ?.getSchema()
  }

  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<PsiReference>) {
    val element = queryParameters.elementToSearch

    val nameDefinition = when (element) {
      is PsiClass -> getSchema(element)?.findEntity(element) ?: return
      is PsiField -> getSchema(element)?.findEntity(element.containingClass ?: return)?.columns?.find { it.psiField == element } ?: return
      else -> return
    }

    val name = nameDefinition.name

    val word = when {
      RoomSqlLexer.needsQuoting(name) -> {
        // We need to figure out how a reference to this element looks like in the IdIndex. We find the first "word" in the quoted name and
        // look for it in the index, as any reference for this table will include this word in its text.
        val processor = CommonProcessors.FindFirstProcessor<WordOccurrence>()
        RoomFindUsagesProvider().wordsScanner.processWords(RoomSqlLexer.getValidName(name), processor)
        processor.foundValue?.let { it.baseText.substring(it.start, it.end) } ?: name
      }
      else -> name
    }

    // Note: QueryExecutorBase is a strange API: the naive way to implement it is to somehow find the references and feed them to the
    // consumer. The "proper" way is to get the optimizer and schedule an index search for references to a given element that include a
    // given text. Scheduled searches get merged and run later. We use the latter API, but:
    //   - We search for the right word (as calculate above).
    //   - We use `scopeDeterminedByUser`, not `effectiveScope`. Effective scope for a private field contains only the file in which it's
    //     defined, which is not how Room works.
    //   - We look for references to the right element: if a table/column name is overriden using annotations, the PSI references may not
    //     point to the class/field itself, but we still want to show these references in "find usages".
    queryParameters.optimizer.searchWord(word, queryParameters.scopeDeterminedByUser, false, nameDefinition.nameElement)
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
