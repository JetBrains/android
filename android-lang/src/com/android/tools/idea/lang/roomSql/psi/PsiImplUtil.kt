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

import com.android.tools.idea.lang.roomSql.refactoring.RoomNameElementManipulator
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

fun getReference(bindParameter: RoomBindParameter): PsiReference? {
  return if (bindParameter.isColonNamedParameter) RoomParameterReference(bindParameter) else null
}

fun getParameterNameAsString(bindParameter: RoomBindParameter): String? {
  return if (isColonNamedParameter(bindParameter)) {
    bindParameter.text.substring(startIndex = 1)
  } else {
    null
  }
}

fun isColonNamedParameter(bindParameter: RoomBindParameter): Boolean {
  val node = bindParameter.node.firstChildNode ?: return false
  return node.elementType == RoomPsiTypes.NAMED_PARAMETER && node.chars.startsWith(':')
}

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
  return if (withClauseTable.withClauseTableDef.columnDefinitionNameList.isNotEmpty()) {
    WithClauseTable(withClauseTable)
  } else {
    val tableName = withClauseTable.withClauseTableDef.tableDefinitionName
    AliasedTable(name = tableName.nameAsString, resolveTo = tableName, delegate = SubqueryTable(withClauseTable.selectStatement))
  }
}

fun getSqlTable(table: RoomSingleTableStatementTable): SqlTable? = table.definedTableName.reference.resolveSqlTable()

fun getName(tableAliasName: RoomTableAliasName) = tableAliasName.nameAsString

fun setName(tableAliasName: RoomTableAliasName, newName: String): RoomTableAliasName {
  RoomNameElementManipulator().handleContentChange(tableAliasName, newName)
  return tableAliasName
}

fun getName(tableDefinitionName: RoomTableDefinitionName) = tableDefinitionName.nameAsString

fun setName(tableDefinitionName: RoomTableDefinitionName, newName: String): RoomTableDefinitionName {
  RoomNameElementManipulator().handleContentChange(tableDefinitionName, newName)
  return tableDefinitionName
}

fun getName(columnAliasName: RoomColumnAliasName) = columnAliasName.nameAsString

fun setName(columnAliasName: RoomColumnAliasName, newName: String): RoomColumnAliasName {
  RoomNameElementManipulator().handleContentChange(columnAliasName, newName)
  return columnAliasName
}

fun getName(columnDefinitionName: RoomColumnDefinitionName) = columnDefinitionName.nameAsString

fun setName(columnDefinitionName: RoomColumnDefinitionName, newName: String): RoomColumnDefinitionName {
  RoomNameElementManipulator().handleContentChange(columnDefinitionName, newName)
  return columnDefinitionName
}

