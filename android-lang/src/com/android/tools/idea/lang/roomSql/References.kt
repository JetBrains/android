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
import com.android.tools.idea.lang.roomSql.psi.QueryWithSqlContext
import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil

interface SqlReference : PsiReference {
  val sqlContext: SqlContext?
  val warnIfUnresolved: Boolean
    get() = sqlContext?.isSound == true
}

/**
 * A [PsiReference] pointing from the column name in SQL to the Java PSI element defining the column name.
 *
 * @see EntityColumn.nameElement
 */
class RoomColumnPsiReference(private val columnName: RoomColumnName) : PsiReferenceBase.Poly<RoomColumnName>(columnName), SqlReference {

  override val sqlContext get() = chooseContext(columnName)

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      sqlContext
          ?.columns
          ?.asSequence()
          ?.filter { it.name.equals(columnName.nameAsString, ignoreCase = true) }
          ?.mapNotNull { it.nameElement.element }
          ?.toList()
          .orEmpty())

  override fun getVariants(): Array<Any> =
      sqlContext
          ?.columns
          ?.map { column ->
            // Columns that come from Java fields will most likely use camelCase, starting with a lower-case letter. By default code
            // completion is configured to only check the case of first letter (see Settings), so if the user types `isv` we will
            // suggest e.g. `isValid`. Keeping this flag set to true means that the inserted string is exactly the same as the field
            // name (`isValid`) which seems a good UX. See the below for why table name completion is configured differently.
            //
            // The interactions between this flag and the user-level setting are non-obvious, so consider all cases before changing.
            val lookupElement =
                LookupElementBuilder.create(column.element, RoomSqlLexer.getValidName(column.name)).withCaseSensitivity(true)

            // Try to set the type text from the underlying field's java type, if present.
            // TODO: Use the effective SQL type.
            column.element.element.let { it as? PsiField}?.type?.presentableText
                ?.let { lookupElement.withTypeText(it) }
                ?: lookupElement
          }
          ?.toTypedArray<Any>()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY
}


/**
 * A [PsiReference] pointing from the table name in SQL to the Java PSI element defining the table name.
 *
 * @see Entity.nameElement
 */
class RoomTablePsiReference(private val tableName: RoomTableName) : PsiReferenceBase.Poly<RoomTableName>(tableName), SqlReference {

  override val sqlContext get() = chooseContext(tableName)

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = PsiElementResolveResult.createResults(
      sqlContext
          ?.matchingTables(tableName)
          ?.mapNotNull { it.nameElement.element }
          .orEmpty())

  override fun getVariants(): Array<Any> =
      sqlContext
          ?.availableTables
          ?.mapNotNull { table ->
            val psiClass = (table.element.element as? PsiClass) ?: return@mapNotNull null

            LookupElementBuilder.create(psiClass, RoomSqlLexer.getValidName(table.name))
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
          ?.toTypedArray<Any>()
          ?: ArrayUtil.EMPTY_OBJECT_ARRAY
}

/** Picks the [SqlContext] to use for a given query. */
private fun chooseContext(element: PsiElement): SqlContext? {
  return PsiTreeUtil.getParentOfType(element, QueryWithSqlContext::class.java)?.sqlContext
      ?: RoomSchemaManager.getInstance(element)?.schema
}

