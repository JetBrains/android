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
package com.android.tools.idea.lang.androidSql.resolution

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlAlterTableStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlColumnAliasName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlColumnRefExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlDeleteStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlExpression
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFromClause
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlInsertStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlJoinConstraint
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlResultColumn
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlSelectCoreSelect
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlSelectStatement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlTableElement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlUpdateStatement
import com.android.tools.idea.lang.androidSql.psi.HasWithClause
import com.android.tools.idea.lang.androidSql.sqlContext
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.intellij.util.containers.Stack

/**
 * Processes all [AndroidSqlTable]s that are defined for a given [start] [PsiElement].
 *
 * This includes tables in the schema (handled by [AndroidSqlContext.processTables]) as well as views using a `WITH` clause.
 */
fun processDefinedSqlTables(start: PsiElement, processor: Processor<AndroidSqlTable>): Boolean {
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
      is AndroidSqlFile -> return current.sqlContext?.processTables(processor) ?: true
    }

    current = current.parent
  }

  return true
}

/**
 * Processes all [AndroidSqlTable]s whose columns (as well as the table themselves) are in scope for a given [start] [PsiElement].
 *
 * The implementation is similar to a DFS traversal of a PSI subgraph, where the next nodes to visit are chosen based on the current node
 * type and the direction from which we've arrived.
 *
 * @see pushNextElements
 * @return false if the [processor] is finished, true otherwise.
 */
fun processSelectedSqlTables(start: PsiElement, processor: Processor<AndroidSqlTable>): Boolean {

  val stack = Stack<NextStep>()
  stack.push(NextStep(start, previous = null))

  while (stack.isNotEmpty()) {
    val (element, previous) = stack.pop()
    if (element is AndroidSqlTableElement && previous?.parent != element && element.sqlTable?.let(processor::process) == false) return false
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
      is AndroidSqlTableElement -> {
        // There are no table definitions inside table definitions. Also, this stops us from walking down subqueries.
        return
      }
      is AndroidSqlExpression -> {
        // Expressions don't define tables, but can contain deep subtrees for subqueries.
        return
      }
      is AndroidSqlFromClause -> element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      else -> element.children.forEach { stack.push(nextStep(it)) }
    }
  }
  else {
    // Stop walking up, no point leaving the SQL tree.
    if (element is AndroidSqlFile) return

    // Push the parent on the stack, we may be in a nested subexpression so need to find all selected tables.
    element.parent?.pushOnStack()

    // Check if something should be pushed on top of the parent, based on the current node.
    when (element) {
      is AndroidSqlSelectCoreSelect -> {
        // If we came from 'FROM_CLAUSE' we don't want to resolve any columns by using 'FROM_CLAUSE' or 'RESULT_COLUMNS'.
        if (previous != element.fromClause) {
          // Prevent infinite loop.
          if (previous != element.resultColumns) {
            element.resultColumns.pushOnStack()
          }

          element.fromClause?.pushOnStack()
        }
      }
      is AndroidSqlSelectStatement -> {
        if (previous == element.orderClause) {
          // We need to process only first selectCore because the column names of the first query determine the column names of the combined result set.
          // Look at https://www.sqlite.org/lang_select.html#orderby
          val selectCore = element.selectCoreList.firstOrNull()
          selectCore?.selectCoreSelect?.resultColumns?.pushOnStack()
          selectCore?.selectCoreSelect?.fromClause?.pushOnStack()
        }
      }
      is AndroidSqlDeleteStatement -> {
        element.singleTableStatementTable.pushOnStack()
      }
      is AndroidSqlUpdateStatement -> {
        element.singleTableStatementTable?.pushOnStack()
      }
      is AndroidSqlInsertStatement -> {
        if (previous == element.insertColumns) {
          element.singleTableStatementTable.pushOnStack()
        }
      }
      is AndroidSqlAlterTableStatement -> {
        element.singleTableStatementTable.pushOnStack()
      }
      is AndroidSqlFromClause -> {
        if (previous is AndroidSqlJoinConstraint) element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      }
    }
  }
}

/**
 * Returns corresponding [AndroidSqlColumn] if column is defined by [AndroidSqlExpression] otherwise (SELECT *, tablename.* FROM ...) returns null.
 *
 * We use this function within some of [AndroidSqlTable.processColumns] implementations, when we process column from [AndroidSqlResultColumn] section of query.
 *
 * In order to avoid infinite resolving process we keep track of tables that we already started the resolution process for in [sqlTablesInProcess].
 * In can happen because we invoke [AndroidSqlColumnPsiReference.resolveColumn] in this function.
 * @see AndroidSqlColumnPsiReference.resolveColumn
 */
fun computeSqlColumn(resultColumn: AndroidSqlResultColumn, sqlTablesInProcess: MutableSet<PsiElement>): AndroidSqlColumn? {

  fun wrapInAlias(column: AndroidSqlColumn, alias: AndroidSqlColumnAliasName?): AndroidSqlColumn {
    if (alias == null) return column
    return AliasedColumn(column, alias.nameAsString, alias)
  }

  if (resultColumn.expression != null) {
    if (resultColumn.expression is AndroidSqlColumnRefExpression) { // "SELECT id FROM ..."
      val columnRefExpr = resultColumn.expression as AndroidSqlColumnRefExpression
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
