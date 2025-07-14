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

package com.android.tools.idea.lang.androidSql.psi

import com.android.tools.idea.lang.androidSql.refactoring.AndroidSqlNameElementManipulator
import com.android.tools.idea.lang.androidSql.resolution.AliasColumnsTable
import com.android.tools.idea.lang.androidSql.resolution.AliasedTable
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumnPsiReference
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlDefinedTablePsiReference
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlParameterReference
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlSelectedTablePsiReference
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.QualifiedColumnPsiReference
import com.android.tools.idea.lang.androidSql.resolution.SubqueryTable
import com.android.tools.idea.lang.androidSql.resolution.UnqualifiedColumnPsiReference
import com.android.tools.idea.lang.androidSql.resolution.WithClauseTable
import com.intellij.psi.PsiReference


fun getReference(tableName: AndroidSqlSelectedTableName): AndroidSqlSelectedTablePsiReference {
  return AndroidSqlSelectedTablePsiReference(tableName)
}

fun getReference(tableName: AndroidSqlDefinedTableName): AndroidSqlDefinedTablePsiReference {
  return AndroidSqlDefinedTablePsiReference(tableName, acceptViews = tableName.parent !is AndroidSqlSingleTableStatementTable)
}

fun getReference(columnName: AndroidSqlColumnName): AndroidSqlColumnPsiReference {
  val parent = columnName.parent
  if (parent is AndroidSqlColumnRefExpression) {
    val tableName = parent.selectedTableName
    if (tableName != null) {
      return QualifiedColumnPsiReference(columnName, tableName)
    }
  }

  return UnqualifiedColumnPsiReference(columnName)
}

fun getReference(bindParameter: AndroidSqlBindParameter): PsiReference? {
  return if (bindParameter.isColonNamedParameter) AndroidSqlParameterReference(bindParameter) else null
}

fun getParameterNameAsString(bindParameter: AndroidSqlBindParameter): String? {
  return if (isColonNamedParameter(bindParameter)) {
    bindParameter.text.substring(startIndex = 1)
  } else {
    null
  }
}

fun isColonNamedParameter(bindParameter: AndroidSqlBindParameter): Boolean {
  val node = bindParameter.node.firstChildNode ?: return false
  return node.elementType == AndroidSqlPsiTypes.NAMED_PARAMETER && node.chars.startsWith(':')
}

fun getSqlTable(fromTable: AndroidSqlFromTable): AndroidSqlTable? {
  val realTable = fromTable.definedTableName.reference.resolveSqlTable() ?: return null
  val alias = fromTable.tableAliasName
  return if (alias != null) AliasedTable(realTable, name = alias.nameAsString, resolveTo = alias) else realTable
}

fun getSqlTable(subquery: AndroidSqlSelectSubquery): AndroidSqlTable? {
  val subqueryTable = SubqueryTable(subquery.selectStatement ?: return null)
  val alias = subquery.tableAliasName
  return if (alias == null) subqueryTable else AliasedTable(subqueryTable, name = alias.nameAsString, resolveTo = alias)
}

fun getSqlTable(resultColumns: AndroidSqlResultColumns): AndroidSqlTable? = AliasColumnsTable(resultColumns)

fun getTableDefinition(withClauseTable: AndroidSqlWithClauseTable): AndroidSqlTable? {
  return if (withClauseTable.withClauseTableDef.columnDefinitionNameList.isNotEmpty()) {
    WithClauseTable(withClauseTable)
  } else {
    val tableName = withClauseTable.withClauseTableDef.tableDefinitionName
    AliasedTable(
      name = tableName.nameAsString,
      resolveTo = tableName,
      delegate = SubqueryTable(withClauseTable.selectStatement ?: return null)
    )
  }
}

fun getSqlTable(table: AndroidSqlSingleTableStatementTable): AndroidSqlTable? = table.definedTableName.reference.resolveSqlTable()

fun getName(tableAliasName: AndroidSqlTableAliasName) = tableAliasName.nameAsString

fun setName(tableAliasName: AndroidSqlTableAliasName, newName: String): AndroidSqlTableAliasName {
  AndroidSqlNameElementManipulator().handleContentChange(tableAliasName, newName)
  return tableAliasName
}

fun getName(tableDefinitionName: AndroidSqlTableDefinitionName) = tableDefinitionName.nameAsString

fun setName(tableDefinitionName: AndroidSqlTableDefinitionName, newName: String): AndroidSqlTableDefinitionName {
  AndroidSqlNameElementManipulator().handleContentChange(tableDefinitionName, newName)
  return tableDefinitionName
}

fun getName(columnAliasName: AndroidSqlColumnAliasName) = columnAliasName.nameAsString

fun setName(columnAliasName: AndroidSqlColumnAliasName, newName: String): AndroidSqlColumnAliasName {
  AndroidSqlNameElementManipulator().handleContentChange(columnAliasName, newName)
  return columnAliasName
}

fun getName(columnDefinitionName: AndroidSqlColumnDefinitionName) = columnDefinitionName.nameAsString

fun setName(columnDefinitionName: AndroidSqlColumnDefinitionName, newName: String): AndroidSqlColumnDefinitionName {
  AndroidSqlNameElementManipulator().handleContentChange(columnDefinitionName, newName)
  return columnDefinitionName
}
