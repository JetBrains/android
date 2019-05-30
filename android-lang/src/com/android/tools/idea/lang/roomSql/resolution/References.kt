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
package com.android.tools.idea.lang.roomSql.resolution

import com.android.tools.idea.lang.roomSql.NotRenamableElement
import com.android.tools.idea.lang.roomSql.RoomAnnotations
import com.android.tools.idea.lang.roomSql.psi.RoomBindParameter
import com.android.tools.idea.lang.roomSql.psi.RoomColumnName
import com.android.tools.idea.lang.roomSql.psi.RoomDefinedTableName
import com.android.tools.idea.lang.roomSql.psi.RoomNameElement
import com.android.tools.idea.lang.roomSql.psi.RoomResultColumns
import com.android.tools.idea.lang.roomSql.psi.RoomSelectCoreSelect
import com.android.tools.idea.lang.roomSql.psi.RoomSelectedTableName
import com.android.tools.idea.lang.roomSql.psi.RoomSqlFile
import com.android.tools.idea.lang.roomSql.refactoring.RoomNameElementManipulator
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil.EMPTY_OBJECT_ARRAY
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

interface RoomColumnPsiReference : PsiReference {
  /**
   * Returns [SqlColumn] for the underlying (referencing) element of the reference.
   *
   * We find [SqlColumn] by traversing tables. Sometimes one table is defined through another and so on and it leads to invocation [resolveColumn]
   * within another [resolveColumn] process.
   * We want to avoid an infinite [resolveColumn] process (e.g. invalid recursive definitions cause it). In order to do it we maintain [sqlTablesInProcess].
   * [sqlTablesInProcess] contains tables for which we are currently trying to resolve columns.
   *
   * Example: Table A contains all columns of table B and table B contains all column of table C. When we try to resolve column belongs to A
   * eventually we can invoke [resolveColumn] for column belongs to C; at that moment [sqlTablesInProcess] contains [PsiElement]s
   * that define table A and table B.
   */
  fun resolveColumn(sqlTablesInProcess: MutableSet<PsiElement>): SqlColumn?

  override fun getElement(): RoomNameElement

  override fun resolve(): PsiElement? {
    val realColumn = resolveColumn(HashSet()) ?: return null

    return if (realColumn.name.equals(element.nameAsString, ignoreCase = true)) {
      realColumn.resolveTo
    } else {
      /**
       * Case when we found column by alternative name that user didn't define which means the reference should not be used for renaming
       * the column e.g "rowid" or "oid" or "_rowid_" columns
       */
      NotRenamableElement(realColumn.resolveTo)
    }
  }
}

/**
 * A [PsiReference] pointing from the column name in SQL to the PSI element defining the column name.
 *
 * @see RoomFieldColumn.nameElement
 */
class UnqualifiedColumnPsiReference(columnName: RoomColumnName) : PsiReferenceBase<RoomColumnName>(columnName), RoomColumnPsiReference {

  override fun resolveColumn(sqlTablesInProcess: MutableSet<PsiElement>): SqlColumn? {
    val processor = FindColumnByNameProcessor(element.nameAsString)
    processSelectedSqlTables(element, AllColumnsProcessor(processor, sqlTablesInProcess))
    return processor.foundValue
  }

  override fun getVariants(): Array<Any> {
    val columnsProcessor = CollectUniqueNamesProcessor<SqlColumn>()
    val tablesProcessor = AllColumnsProcessor(columnsProcessor, HashSet())
    processSelectedSqlTables(element, tablesProcessor)

    if (tablesProcessor.tablesProcessed == 0) {
      // Let's try to be helpful in the common case of a SELECT query with no FROM clause, even though referencing any columns will result
      // in an invalid query for now, until the FROM clause is written.
      val parentSelect = PsiTreeUtil.getParentOfType(element, RoomResultColumns::class.java)?.parent?.let { it as RoomSelectCoreSelect }
      if (parentSelect != null && parentSelect.fromClause == null) {
        (element.containingFile as RoomSqlFile).processTables(tablesProcessor)
      }
    }

    return buildVariants(columnsProcessor.result)
  }
}

/** Reference to a column within a known table, e.g. `SELECT user.name FROM user`. */
class QualifiedColumnPsiReference(
  columnName: RoomColumnName,
  private val tableName: RoomSelectedTableName
) : PsiReferenceBase<RoomColumnName>(columnName), RoomColumnPsiReference {
  private fun resolveTable() = RoomSelectedTablePsiReference(tableName).resolveSqlTable()

  override fun resolveColumn(sqlTablesInProcess: MutableSet<PsiElement>): SqlColumn? {
    val table = resolveTable() ?: return null
    val processor = FindColumnByNameProcessor(element.nameAsString)
    table.processColumns(processor, sqlTablesInProcess)
    return processor.foundValue
  }

  override fun getVariants(): Array<Any> {
    val table = resolveTable() ?: return emptyArray()
    val processor = CollectUniqueNamesProcessor<SqlColumn>()
    table.processColumns(processor, HashSet())
    return buildVariants(processor.result)
  }
}

private fun buildVariants(result: Collection<SqlColumn>): Array<Any> {
  return result
    .filter { column -> column !is RoomRowidColumn}
    .map { column ->
      LookupElementBuilder.create(column.definingElement, RoomNameElementManipulator.getValidName(column.name!!))
        .withTypeText(column.type?.typeName)
        .withTailText(if (column.isPrimaryKey) " (integer primary key)" else null)
        // Columns that come from Java fields will most likely use camelCase, starting with a lower-case letter. By default code
        // completion is configured to only check the case of first letter (see Settings), so if the user types `isv` we will
        // suggest e.g. `isValid`. Keeping this flag set to true means that the inserted string is exactly the same as the field
        // name (`isValid`) which seems a good UX. See the below for why table name completion is configured differently.
        //
        // The interactions between this flag and the user-level setting are non-obvious, so consider all cases before changing.
        .withCaseSensitivity(true)
    }
    .toTypedArray()
}

/**
 * A [PsiReference] pointing from the selected table name in SQL to the PSI element defining the table name.
 *
 * @see RoomTable.nameElement
 */
class RoomSelectedTablePsiReference(
  tableName: RoomSelectedTableName
) : PsiReferenceBase<RoomSelectedTableName>(tableName) {

  override fun resolve(): PsiElement? = resolveSqlTable()?.resolveTo

  fun resolveSqlTable(): SqlTable? {
    val processor = FindTableByNameProcessor(element.nameAsString)
    processSelectedSqlTables(element, processor)
    return processor.foundValue
  }

  override fun getVariants(): Array<Any> {
    val processor = CollectUniqueNamesProcessor<SqlTable>()
    processSelectedSqlTables(element, processor)
    return processor.result.map(::lookupElementForTable).toTypedArray()
  }
}

class RoomDefinedTablePsiReference(
  tableName: RoomDefinedTableName,
  private val acceptViews: Boolean = true
) : PsiReferenceBase<RoomDefinedTableName>(tableName) {
  override fun resolve(): PsiElement? = resolveSqlTable()?.resolveTo

  fun resolveSqlTable(): SqlTable? {
    val processor = FindTableByNameProcessor(element.nameAsString)
    processDefinedSqlTables(element, if (acceptViews) processor else IgnoreViewsProcessor(processor))
    return processor.foundValue
  }

  override fun getVariants(): Array<Any> {
    val processor = CollectUniqueNamesProcessor<SqlTable>()
    processDefinedSqlTables(element, if (acceptViews) processor else IgnoreViewsProcessor(processor))
    return processor.result.map(::lookupElementForTable).toTypedArray()
  }
}

private fun lookupElementForTable(table: SqlTable): LookupElement {
  val element = table.definingElement
  return LookupElementBuilder.create(element, RoomNameElementManipulator.getValidName(table.name!!))
    .withTypeText((element as? PsiClass)?.qualifiedName, true)
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

/**
 * [PsiReference] from a SQL bind parameter to an argument of the query method for this piece of SQL.
 *
 * Example:
 *
 * ```
 * @Query("select * from user where userId = :id")
 * List<User> getById(int id);
 * ```
 */
class RoomParameterReference(parameter: RoomBindParameter) : PsiReferenceBase<RoomBindParameter>(parameter) {
  override fun resolve(): PsiElement? {
    val parameterName = element.parameterNameAsString ?: return null
    return findQueryMethod()?.uastParameters?.find { it.name == parameterName }?.sourcePsi
  }

  override fun getVariants(): Array<Any> {
    return findQueryMethod()
             ?.uastParameters
             ?.mapNotNull { (it.sourcePsi as? PsiNamedElement)?.let(LookupElementBuilder::create) }
             ?.toTypedArray<Any>()
           ?: EMPTY_OBJECT_ARRAY
  }

  private fun findQueryMethod(): UMethod? {
    return (element.containingFile as? RoomSqlFile)
      ?.findHostRoomAnnotation()
      ?.takeIf { RoomAnnotations.QUERY.isEquals(it.qualifiedName) }
      ?.getParentOfType<UAnnotated>()
      as? UMethod
  }
}
