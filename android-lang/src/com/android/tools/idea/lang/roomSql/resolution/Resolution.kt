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

import com.android.tools.idea.lang.roomSql.psi.HasWithClause
import com.android.tools.idea.lang.roomSql.psi.RoomColumnAliasName
import com.android.tools.idea.lang.roomSql.psi.RoomColumnRefExpression
import com.android.tools.idea.lang.roomSql.psi.RoomDeleteStatement
import com.android.tools.idea.lang.roomSql.psi.RoomExpression
import com.android.tools.idea.lang.roomSql.psi.RoomFromClause
import com.android.tools.idea.lang.roomSql.psi.RoomInsertStatement
import com.android.tools.idea.lang.roomSql.psi.RoomJoinConstraint
import com.android.tools.idea.lang.roomSql.psi.RoomResultColumn
import com.android.tools.idea.lang.roomSql.psi.RoomSelectCoreSelect
import com.android.tools.idea.lang.roomSql.psi.RoomSelectStatement
import com.android.tools.idea.lang.roomSql.psi.RoomSqlFile
import com.android.tools.idea.lang.roomSql.psi.RoomUpdateStatement
import com.android.tools.idea.lang.roomSql.psi.SqlTableElement
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.intellij.util.containers.Stack

/**
 * Processes all [SqlTable]s that are defined for a given [start] [PsiElement].
 *
 * This includes tables in the schema (handled by [RoomSqlFile.processTables]) as well as views using a `WITH` clause.
 */
fun processDefinedSqlTables(start: PsiElement, processor: Processor<SqlTable>): Boolean {
  var current = start.parent
  while (current != null) {
    when (current) {
      is HasWithClause -> {

        val tables = current.withClause?.withClauseTableList
        if (tables != null) {
          for (table in tables) {
            if (!processor.process(table.tableDefinition)) return false
          }
        }
      }
      is RoomSqlFile -> return current.processTables(processor)
    }

    current = current.parent
  }

  return true
}

/**
 * Processes all [SqlTable]s whose columns (as well as the table themselves) are in scope for a given [start] [PsiElement].
 *
 * The implementation is similar to a DFS traversal of a PSI subgraph, where the next nodes to visit are chosen based on the current node
 * type and the direction from which we've arrived.
 *
 * @see pushNextElements
 * @return false if the [processor] is finished, true otherwise.
 */
fun processSelectedSqlTables(start: PsiElement, processor: Processor<SqlTable>): Boolean {

  val stack = Stack<NextStep>()
  stack.push(NextStep(start, previous = null))

  while (stack.isNotEmpty()) {
    val (element, previous) = stack.pop()
    if (element is SqlTableElement && previous?.parent != element && element.sqlTable?.let(processor::process) == false) return false
    pushNextElements(stack, element, previous, walkingDown = (previous == element.context))
  }

  return true
}

/**
 * Represents the next node to visit in PSI traversal.
 *
 * @see processSelectedSqlTables
 */
private data class NextStep(val next: PsiElement, val previous: PsiElement?)

/**
 * Determines the next elements to visit during the PSI traversal.
 *
 * @see processSelectedSqlTables
 */
private fun pushNextElements(
  stack: Stack<NextStep>,
  element: PsiElement,
  previous: PsiElement?,
  walkingDown: Boolean
) {
  fun nextStep(next: PsiElement, newPrevious: PsiElement? = element) = NextStep(next, previous = newPrevious)
  fun PsiElement.pushOnStack() = stack.push(nextStep(this))

  if (walkingDown) {
    when (element) {
      is SqlTableElement -> {
        // There are no table definitions inside table definitions. Also, this stops us from walking down subqueries.
        return
      }
      is RoomExpression -> {
        // Expressions don't define tables, but can contain deep subtrees for subqueries.
        return
      }
      is RoomFromClause -> element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      else -> element.children.forEach { stack.push(nextStep(it)) }
    }
  }
  else {
    // Stop walking up, no point leaving the SQL tree.
    if (element is RoomSqlFile) return

    // Push the parent on the stack, we may be in a nested subexpression so need to find all selected tables.
    element.parent?.pushOnStack()

    // Check if something should be pushed on top of the parent, based on the current node.
    when (element) {
      is RoomSelectCoreSelect -> {
        // If we came from 'FROM_CLAUSE' we don't want to resolve any columns by using 'FROM_CLAUSE' or 'RESULT_COLUMNS'.
        if (previous != element.fromClause) {
          // Prevent infinite loop.
          if (previous != element.resultColumns) {
            element.resultColumns.pushOnStack()
          }

          element.fromClause?.pushOnStack()
        }
      }
      is RoomSelectStatement -> {
        if (previous == element.orderClause) {
          // We need to process only first selectCore because the column names of the first query determine the column names of the combined result set.
          // Look at https://www.sqlite.org/lang_select.html#orderby
          val selectCore = element.selectCoreList.firstOrNull()
          selectCore?.selectCoreSelect?.resultColumns?.pushOnStack()
          selectCore?.selectCoreSelect?.fromClause?.pushOnStack()
        }
      }
      is RoomDeleteStatement -> {
        element.singleTableStatementTable.pushOnStack()
      }
      is RoomUpdateStatement -> {
        element.singleTableStatementTable?.pushOnStack()
      }
      is RoomInsertStatement -> {
        if (previous == element.insertColumns) {
          element.singleTableStatementTable.pushOnStack()
        }
      }
      is RoomFromClause -> {
        if (previous is RoomJoinConstraint) element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      }
    }
  }
}

/**
 * Returns corresponding [SqlColumn] if column is defined by [RoomExpression] otherwise (SELECT *, tablename.* FROM ...) returns null.
 *
 * We use this function within some of [SqlTable.processColumns] implementations, when we process column from [RoomResultColumn] section of query.
 *
 * In order to avoid infinite resolving process we keep track of tables that we already started the resolution process for in [sqlTablesInProcess].
 * In can happen because we invoke [RoomColumnPsiReference.resolveColumn] in this function.
 * @see RoomColumnPsiReference.resolveColumn
 */
fun computeSqlColumn(resultColumn: RoomResultColumn, sqlTablesInProcess: MutableSet<PsiElement>): SqlColumn? {

  fun wrapInAlias(column: SqlColumn, alias: RoomColumnAliasName?): SqlColumn {
    if (alias == null) return column
    return AliasedColumn(column, alias.nameAsString, alias)
  }

  if (resultColumn.expression != null) {
    if (resultColumn.expression is RoomColumnRefExpression) { // "SELECT id FROM ..."
      val columnRefExpr = resultColumn.expression as RoomColumnRefExpression
      val referencedColumn = columnRefExpr.columnName.reference.resolveColumn(sqlTablesInProcess)
      val sqlColumn = when {
        referencedColumn != null -> referencedColumn
        resultColumn.columnAliasName != null -> {
          // We have an invalid reference which is given a name, we can still define a named column so that errors don't propagate.
          ExprColumn(columnRefExpr.columnName)
        }
        else -> return null
      }

      return wrapInAlias(sqlColumn, resultColumn.columnAliasName)
    }

    // "SELECT id * 2 FROM ..."
    return wrapInAlias(ExprColumn(resultColumn.expression!!), resultColumn.columnAliasName)
  }

  // "SELECT * FROM ..."; "SELECT tablename.* FROM ..."
  return null
}
