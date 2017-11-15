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

@file:JvmName("PsiImplUtil")

package com.android.tools.idea.lang.roomSql.psi

import com.android.tools.idea.lang.roomSql.resolution.*
import com.intellij.psi.PsiReference


fun getReference(tableName: RoomSelectedTableName): RoomSelectedTablePsiReference {
  return RoomSelectedTablePsiReference(tableName)
}

fun getReference(tableName: RoomDefinedTableName): RoomDefinedTablePsiReference {
  return RoomDefinedTablePsiReference(tableName, acceptViews = tableName.parent !is RoomSingleTableStatementTable)
}

fun getReference(columnName: RoomColumnName): RoomColumnPsiReference {
  val parent = columnName.parent
  if (parent is RoomColumnRefExpression) {
    val tableName = parent.selectedTableName
    if (tableName != null) {
      return QualifiedColumnPsiReference(columnName, tableName)
    }
  }

  return UnqualifiedColumnPsiReference(columnName)
}

fun getReference(bindParameter: RoomBindParameter): PsiReference? = RoomParameterReference(bindParameter)

fun getParameterNameAsString(bindParameter: RoomBindParameter) = bindParameter.text.substring(startIndex = 1)

fun getSqlTable(fromTable: RoomFromTable): SqlTable? {
  val realTable = fromTable.definedTableName.reference.resolveSqlTable() ?: return null
  val alias = fromTable.tableAliasName
  return if (alias != null) AliasedTable(realTable, name = alias.nameAsString, resolveTo = alias) else realTable
}

fun getSqlTable(subquery: RoomSubquery): SqlTable? {
  val subqueryTable = SubqueryTable(subquery.selectStatement)
  val alias = subquery.tableAliasName
  return if (alias == null) subqueryTable else AliasedTable(subqueryTable, name = alias.nameAsString, resolveTo = alias)
}

fun getTableDefinition(withClauseTable: RoomWithClauseTable): SqlTable {
  val tableName = withClauseTable.withClauseTableDef.tableDefinitionName
  return AliasedTable(name = tableName.nameAsString, resolveTo = tableName, delegate = SubqueryTable(withClauseTable.selectStatement))
}

fun getSqlTable(table: RoomSingleTableStatementTable): SqlTable? = table.definedTableName.reference.resolveSqlTable()
