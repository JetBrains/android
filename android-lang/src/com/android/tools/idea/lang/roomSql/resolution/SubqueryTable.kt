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

import com.android.tools.idea.lang.roomSql.psi.RoomColumnAliasName
import com.android.tools.idea.lang.roomSql.psi.RoomColumnRefExpression
import com.android.tools.idea.lang.roomSql.psi.RoomSelectStatement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.Processor

/**
 * A [SqlTable] that represents a given subquery. Keeps track of what columns are returned by the query. If the query "selects" an
 * expression without assigning it a column name, it's assumed to be unnamed and so will be ignored by the processors.
 */
class SubqueryTable(private val selectStmt: RoomSelectStatement) : SqlTable {
  override val name get() = null
  override val definingElement get() = selectStmt
  override val isView: Boolean get() = true

  override fun processColumns(processor: Processor<SqlColumn>): Boolean {
    for (selectCore in selectStmt.selectCoreList) {
      ProgressManager.checkCanceled()

      val resultColumns = selectCore.selectCoreSelect?.resultColumns?.resultColumnList ?: continue
      columns@ for (resultColumn in resultColumns) {
        when {
          resultColumn.expression is RoomColumnRefExpression -> { // SELECT id FROM ...
            val columnRefExpr = resultColumn.expression as RoomColumnRefExpression
            val referencedColumn = columnRefExpr.columnName.reference.resolveColumn()
            val sqlColumn = when {
              referencedColumn != null -> referencedColumn
              resultColumn.columnAliasName != null -> {
                // We have an invalid reference which is given a name, we can still define a named column so that errors don't propagate.
                ExprColumn(columnRefExpr.columnName)
              }
              else -> continue@columns
            }

            if (!processor.process(wrapInAlias(sqlColumn, resultColumn.columnAliasName))) return false
          }
          resultColumn.expression != null -> { // SELECT id * 2 FROM ...
            if (!processor.process(wrapInAlias(ExprColumn(resultColumn.expression!!), resultColumn.columnAliasName))) return false
          }
          resultColumn.selectedTableName != null -> { // "SELECT user.* FROM ..."
            val sqlTable = resultColumn.selectedTableName?.reference?.resolveSqlTable() ?: continue@columns
            if (!sqlTable.processColumns(processor)) return false
          }
          else -> { // "SELECT * FROM ..."
            if (!processSelectedSqlTables(resultColumn, AllColumnsProcessor(processor))) return false
          }
        }
      }
    }

    return true
  }

  private fun wrapInAlias(column: SqlColumn, alias: RoomColumnAliasName?): SqlColumn {
    if (alias == null) return column
    return AliasedColumn(column, alias.nameAsString, alias)
  }
}
