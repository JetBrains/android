/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.model

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.lang.androidSql.parser.AndroidSqlParserDefinition
import com.android.tools.idea.sqlite.sqlLanguage.getSqliteStatementType
import com.android.tools.idea.sqlite.sqlLanguage.getWrappableStatement
import com.android.tools.idea.sqlite.sqlLanguage.inlineParameterValues
import com.intellij.openapi.project.Project
import java.util.LinkedList

/**
 * Returns a valid [RowIdName] in the context of the list of columns passed as argument.
 *
 * The RowId column has different valid names: rowid, oid and _rowid_. Each one of these names stops
 * being valid for the RowId column if the table has a user-defined column using that name. eg.
 * CREATE TABLE t (oid TEXT), here oid is a user-defined column and can't be used to get the RowId
 * column.
 *
 * If all three names are used for user-define column it's not possible to get the RowId column,
 * unless the table has an integer primary key, in that case the name of the integer primary key
 * column corresponds to the RowId column.
 */
fun getRowIdName(columns: List<SqliteColumn>): RowIdName? {
  // if the db has an integer primary key, that column is also used for rowid.
  // otherwise we need to find the correct alias to use for the rowid column.
  val hasIntegerPrimaryKey =
    columns.any { it.inPrimaryKey && it.affinity == SqliteAffinity.INTEGER }
  return when {
    hasIntegerPrimaryKey -> null
    columns.none { it.name == RowIdName._ROWID_.stringName } -> RowIdName._ROWID_
    columns.none { it.name == RowIdName.ROWID.stringName } -> RowIdName.ROWID
    columns.none { it.name == RowIdName.OID.stringName } -> RowIdName.OID
    else -> null
  }
}

/**
 * Returns a new [SqliteStatement], the text of which is obtained applying [func] to the text of the
 * original [SqliteStatement]. The parameters are the same of the original [SqliteStatement].
 */
fun SqliteStatement.transform(
  newStatementType: SqliteStatementType,
  func: (String) -> String
): SqliteStatement {
  val newStatement = func(this.sqliteStatementText)
  val newStatementStringRepresentation = func(this.sqliteStatementWithInlineParameters)
  return SqliteStatement(
    newStatementType,
    newStatement,
    parametersValues,
    newStatementStringRepresentation
  )
}

@UiThread
fun createSqliteStatement(
  project: Project,
  sqliteStatementText: String,
  parametersValues: List<SqliteValue> = emptyList()
): SqliteStatement {
  val psiElement = AndroidSqlParserDefinition.parseSqlQuery(project, sqliteStatementText)
  val statementStringRepresentation =
    inlineParameterValues(psiElement, LinkedList(parametersValues))
  val statementType = getSqliteStatementType(project, sqliteStatementText)
  val sqliteStatementTextWithNoTrailingSemicolon =
    getWrappableStatement(project, sqliteStatementText)
  return SqliteStatement(
    statementType,
    sqliteStatementTextWithNoTrailingSemicolon,
    parametersValues,
    statementStringRepresentation
  )
}

fun DatabaseInspectorModel.getAllDatabaseIds(): List<SqliteDatabaseId> =
  getOpenDatabaseIds() + getCloseDatabaseIds()

data class DatabaseIdNotFoundException(override val message: String?) : RuntimeException(message)
