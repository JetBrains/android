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
import com.intellij.util.CommonProcessors
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
    when (element) {
      is RoomSqlFile -> return // Stop walking up, no point leaving the SQL tree.
      is RoomSelectCoreSelect -> when (previous) {
        element.fromClause -> pushIfNotNull(element.context) // Keep walking up the tree to find the schema in [RoomSqlFile].
        else -> pushIfNotNull(element.fromClause) // Reverse direction, start downwards traversal of the FROM clause.
      }
      is RoomSelectStmt -> {
        // Visit the WITH clause before continuing up the tree.
        pushIfNotNull(element.context)
        pushIfNotNull(element.withClause)
      }
      is RoomJoinClause -> when (previous) {
        is RoomJoinConstraint -> {
          // Reverse direction, visit all tables in the join.
          element.tableOrSubqueryList.forEach { stack.push(nextStep(it)) }
        }
        else -> pushIfNotNull(element.context) // Keep walking up the tree to find the schema in [RoomSqlFile].
      }
      is RoomTableName -> {
        val context = element.context
        if (context is RoomFromTable || context is RoomWithClauseTableDef) {
          // [element] is part of a table definition that will try to resolve it when asked to create a [SqlTable]. To avoid this circular
          // dependency, skip some nodes and start walking once we are outside of the table definition.
          pushIfNotNull(PsiTreeUtil.getContextOfType(element, SqlTableElement::class.java)?.context)
        } else {
          pushIfNotNull(element.context)
        }
      }
      else -> pushIfNotNull(element.context)
    }
  }

}

/**
 * [Processor] that finds a table/column with a given name.
 */
class FindByNameProcessor<T : SqlDefinition>(private val nameToLookFor: String) : CommonProcessors.FindProcessor<T>() {
  override fun accept(t: T): Boolean = nameToLookFor.equals(t.name, ignoreCase = true)
}

/**
 * [Processor] that records all available tables/columns that have a name.
 */
class CollectUniqueNamesProcessor<T : SqlDefinition> : Processor<T> {
  val map: HashMap<String, T> = hashMapOf()
  val result: Collection<T> get() = map.values

  override fun process(t: T): Boolean {
    val name = t.name
    if (name != null) map.putIfAbsent(name, t)
    return true
  }
}

/**
 * Runs a [delegate] [Processor] on every [SqlColumn] of every processed [SqlTable].
 */
class AllColumnsProcessor(private val delegate: Processor<SqlColumn>) : Processor<SqlTable> {
  var tablesProcessed = 0

  override fun process(t: SqlTable): Boolean {
    t.processColumns(delegate)
    tablesProcessed++
    return true
  }
}

/**
 * A [SqlTable] that represents a given subquery. Keeps track of what columns are returned by the query. If the query "selects" an
 * expression without assigning it a column name, it's assumed to be unnamed and so will be ignored by the processors.
 */
class SubqueryTable(private val selectStmt: RoomSelectStmt) : SqlTable {
  override val name get() = null
  override val definingElement get() = selectStmt

  override fun processColumns(processor: Processor<SqlColumn>): Boolean {
    for (selectCore in selectStmt.selectCoreList) {
      // TODO - support VALUES clause?
      val resultColumns = selectCore.selectCoreSelect?.resultColumns?.resultColumnList ?: continue
      columns@ for (resultColumn in resultColumns) {
        when {
          resultColumn.expr is RoomColumnRefExpr -> { // SELECT id FROM ...
            val columnRefExpr = resultColumn.expr as RoomColumnRefExpr
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
          resultColumn.expr != null -> { // SELECT id * 2 FROM ...
            if (!processor.process(wrapInAlias(ExprColumn(resultColumn.expr!!), resultColumn.columnAliasName))) return false
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
