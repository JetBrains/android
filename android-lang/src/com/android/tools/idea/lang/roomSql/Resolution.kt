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

import com.android.tools.idea.lang.roomSql.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.containers.Stack

/**
 * Processes all [SqlTable]s visible from a given [start] [PsiElement].
 *
 * The implementation is similar to a DFS traversal of a PSI subgraph, where the next nodes to visit are chosen based on the current node
 * type and the direction from which we've arrived.
 *
 * @see pushNextElements
 * @return false if the [processor] is finished, true otherwise.
 */
fun processSqlTables(start: PsiElement, processor: Processor<SqlTable>): Boolean {

  val stack = Stack<NextStep>()
  stack.push(NextStep(start, previous = null))

  while (stack.isNotEmpty()) {
    val (element, previous) = stack.pop()

    when (element) {
      is SqlTableElement -> {
        if (element.sqlTable?.let(processor::process) == false) return false
      }
      is RoomSqlFile -> {
        // RoomSqlFile is responsible for choosing which tables are "in the schema" for the query.
        return element.processTables(processor)
      }
    }

    pushNextElements(stack, element, previous, walkingDown = (previous == element.context))
  }

  return true
}

/**
 * Represents the next node to visit in PSI traversal.
 *
 * @see processSqlTables
 */
private data class NextStep(val next: PsiElement, val previous: PsiElement?)

/**
 * Determines the next elements to visit during the PSI traversal.
 *
 * @see processSqlTables
 */
private fun pushNextElements(
    stack: Stack<NextStep>,
    element: PsiElement,
    previous: PsiElement?,
    walkingDown: Boolean
) {
  fun nextStep(next: PsiElement) = NextStep(next, previous = element)

  fun pushIfNotNull(next: PsiElement?) {
    if (next != null) stack.push(nextStep(next))
  }

  if (walkingDown) {
    when (element) {
      is SqlTableElement -> {
        // There are no table definitions inside table defintions. Also, this stops us from walking down subqueries.
        return
      }
      is RoomJoinClause -> element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      else -> element.children.forEach { stack.push(nextStep(it)) }
    }
  }
  else {
    // The rule for [RoomTableName] below means that `previous` may not be a direct child.
    val child = previous?.let { PsiTreeUtil.findPrevParent(element, it) }

    when (element) {
      is RoomSqlFile -> return // Stop walking up, no point leaving the SQL tree.
      is RoomSelectCoreSelect -> when (child) {
        element.fromClause -> pushIfNotNull(element.parent) // Keep walking up the tree to find the schema in [RoomSqlFile].
        else -> pushIfNotNull(element.fromClause) // Reverse direction, start downwards traversal of the FROM clause.
      }
      is RoomSelectStatement -> {
        // Visit the WITH clause before continuing up the tree.
        pushIfNotNull(element.parent)
        pushIfNotNull(element.withClause)
      }
      is RoomDeleteStatement -> when (child) {
        element.withClause -> pushIfNotNull(element.parent)
        element.singleTableStatementTable -> {
          // Visit the WITH clause before continuing up the tree.
          pushIfNotNull(element.parent)
          pushIfNotNull(element.withClause)
        }
        else -> pushIfNotNull(element.singleTableStatementTable)
      }
      is RoomUpdateStatement -> when (child) {
        element.withClause -> pushIfNotNull(element.parent)
        element.singleTableStatementTable -> {
          // Visit the WITH clause before continuing up the tree.
          pushIfNotNull(element.parent)
          pushIfNotNull(element.withClause)
        }
        else -> pushIfNotNull(element.singleTableStatementTable)
      }
      is RoomInsertStatement -> when (child) {
        element.withClause -> pushIfNotNull(element.parent)
        else -> {
          // Visit the WITH clause before continuing up the tree.
          pushIfNotNull(element.parent)
          pushIfNotNull(element.withClause)
        }
      }
      is RoomJoinClause -> when (child) {
        is RoomJoinConstraint -> {
          // Reverse direction, visit all tables in the join.
          element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
        }
        else -> pushIfNotNull(element.parent) // Keep walking up the tree to find the schema in [RoomSqlFile].
      }
      is RoomTableName -> {
        val parent = element.parent
        if (parent is RoomFromTable || parent is RoomWithClauseTableDef || parent is RoomSingleTableStatementTable) {
          // [element] is part of a table definition that will try to resolve it when asked to create a [SqlTable]. To avoid this circular
          // dependency, skip some nodes and start walking once we are outside of the table definition.
          pushIfNotNull(PsiTreeUtil.getContextOfType(element, SqlTableElement::class.java)?.parent)
        }
        else {
          pushIfNotNull(element.parent)
        }
      }
      else -> pushIfNotNull(element.parent)
    }
  }
}

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
      // TODO - support VALUES clause?
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
          resultColumn.tableName != null -> { // "SELECT user.* FROM ..."
            val sqlTable = resultColumn.tableName?.reference?.resolveSqlTable() ?: continue@columns
            if (!sqlTable.processColumns(processor)) return false
          }
          else -> { // "SELECT * FROM ..."
            if (!processSqlTables(resultColumn, AllColumnsProcessor(processor))) return false
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
