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
package com.android.tools.idea.lang.androidSql.resolution

import com.android.tools.idea.lang.androidSql.NotRenamableElement
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlDefinedTableName
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil

internal val AndroidSqlDefinedTableName.isInternalTable: Boolean
  get() = SQLITE_INTERNAL_TABLES.contains(this.nameAsString)

/**
 * Returns [AndroidSqlTable] that corresponds to an internal SQLite table with [tableNameElement] name.
 */
internal fun getInternalTable(tableNameElement: AndroidSqlDefinedTableName) = when (tableNameElement.nameAsString) {
  SQLITE_SEQUENCE_SCHEMA_NAME -> InternalTable(tableNameElement, SQLITE_SEQUENCE_COLUMNS)
  else -> null
}

private val SQLITE_SEQUENCE_COLUMNS: Map<String, SqlType> by lazy {
  mapOf(
    "name" to JavaFieldSqlType("String"),
    "seq" to JavaFieldSqlType("long")
  )
}

/**
 * Creates an AndroidSqlTable for a table created by SQLite for its own internal use.
 *
 * User can use such a table in @Query. [InternalTable] allows to provide a code completion for columns in such table and prevents
 * highlighting a table and it's columns as "Unresolvable reference".
 * [InternalTable] will not appear in code completion for table names.
 *
 * For an example of an internal table see [SQLITE_SEQUENCE_SCHEMA_NAME].
 * For more details about internal see [https://www.sqlite.org/fileformat2.html#intschema]
 */
internal class InternalTable(tableNameElement: AndroidSqlDefinedTableName, val columns: Map<String, SqlType>) : AndroidSqlTable {
  private class PredefinedTableColumn(
    tableNameElement: AndroidSqlDefinedTableName,
    override val name: String,
    override val type: SqlType?
  ) : AndroidSqlColumn {
    override val definingElement = object : FakePsiElement() {
      override fun getParent() = tableNameElement
      override fun canNavigate(): Boolean = false
      override fun getName(): String = this@PredefinedTableColumn.name
    }
  }

  private val androidSqlColumns = columns.map { PredefinedTableColumn(tableNameElement, it.key, it.value) }
  override val isView = false
  override val name = tableNameElement.nameAsString
  override val definingElement = NotRenamableElement(tableNameElement)

  override fun processColumns(processor: Processor<AndroidSqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>): Boolean {
    return ContainerUtil.process(androidSqlColumns, processor)
  }
}