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

import com.android.tools.idea.lang.roomSql.psi.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.containers.Stack

/**
 * Processes all [SqlTable]s that are defined for a given [start] [PsiElement].
 *
 * This includes tables in the schema (handled by [RoomSqlFile.processTables]) as well as views using a `WITH` clause.
 */
fun processDefinedSqlTables(start: PsiElement, processor: Processor<SqlTable>): Boolean {
  var previous = start
  var current = start.parent
  while (current != null) {
    when (current) {
      is HasWithClause -> {
        // We need to watch out if we started from within a WITH table definition: we need to be careful to avoid infinite recursion when
        // processing ourselves. For now we just don't expose the table in the scope of its own definition or any tables to the left of it.
        // BUG: 69240105.
        // TODO: support for WITH RECURSIVE
        // TODO: support for forward references like this: WITH t1 AS (SELECT * FROM t2), t2 AS (SELECT 1) SELECT * from t1;
        val withClause = current.withClause
        val startSubtree =
            if (previous != withClause) null else PsiTreeUtil.findPrevParent(withClause, start) as? RoomWithClauseTable

        val tables = withClause?.withClauseTableList
        if (tables != null) {
          for (table in tables) {
            if (table == startSubtree) break
            if (!processor.process(table.tableDefinition)) return false
          }
        }
      }
      is RoomSqlFile -> return current.processTables(processor)
    }

    previous = current
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
      is RoomExpression -> {
        // Expressions don't define tables, but can contain deep subtrees for subqueries.
        return
      }
      is RoomJoinClause -> element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      else -> element.children.forEach { stack.push(nextStep(it)) }
    }
  } else {
    // Stop walking up, no point leaving the SQL tree.
    if (element is RoomSqlFile) return

    // Push the parent on the stack, we may be in a nested subexpression so need to find all selected tables.
    pushIfNotNull(element.parent)

    // Check if something should be pushed on top of the parent, based on the current node.
    when (element) {
      is RoomSelectCoreSelect -> {
        if (previous != element.fromClause) pushIfNotNull(element.fromClause)
      }
      is RoomDeleteStatement -> {
        pushIfNotNull(element.singleTableStatementTable)
      }
      is RoomUpdateStatement -> {
        pushIfNotNull(element.singleTableStatementTable)
      }
      is RoomJoinClause -> {
        if (previous is RoomJoinConstraint) element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
      }
    }
  }
}

