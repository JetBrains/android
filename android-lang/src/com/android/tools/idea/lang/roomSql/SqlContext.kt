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

import com.android.tools.idea.lang.roomSql.psi.RoomTableName
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

typealias PsiElementPointer = SmartPsiElementPointer<out PsiElement>

/** Defines common properties for PSI elements that end up defining parts of a SQL schema. */
interface SqlNameDefinition {
  val name: String

  /** [PsiElement] that "created" this definition. */
  val element: PsiElementPointer

  /**
   * [PsiElement] that determines the name of this definition.
   *
   * This may be the same as [element] or e.g. an annotation element.
   */
  val nameElement: PsiElementPointer
}

interface SqlTable : SqlNameDefinition {
  val columns: Collection<SqlColumn>
}

interface SqlColumn : SqlNameDefinition

/**
 * Context in which column and table references can be resolved.
 *
 * @see RoomSchema
 */
interface SqlContext {
  /** Columns that can be referenced. */
  val columns: Collection<SqlColumn>

  /** Tables that can be selected from. */
  val availableTables: Collection<SqlTable>

  /** Whether this [SqlContext] can guarantee that valid references will be accepted by SQLite and invalid will be rejected. */
  val isSound: Boolean

  fun matchingTables(name: RoomTableName) = availableTables.filter { it.name.equals(name.nameAsString, ignoreCase = true) }
}

/**
 * [SqlContext] for a select statement with a single source table.
 *
 * It limits the available columns to those present in the selected table.
 */
class SingleTableContext(table: RoomTableName) : SqlContext {
  override val columns: Collection<SqlColumn>
  override val availableTables: Collection<SqlTable>
  override val isSound = true

  init {
    val schema = RoomSchemaManager.getInstance(table)?.schema
    columns = schema?.matchingTables(table)?.flatMap { it.columns } ?: emptySet()
    availableTables = schema?.availableTables ?: emptySet()
  }
}
