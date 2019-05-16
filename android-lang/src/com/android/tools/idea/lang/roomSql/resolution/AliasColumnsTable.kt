/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.lang.roomSql.psi.RoomResultColumns
import com.intellij.psi.PsiElement
import com.intellij.util.Processor

/**
 * A synthetic [SqlTable] that contains aliases defined in the query.
 *
 * Used to resolve references to these aliases from other parts of the query, e.g. the WHERE clause.
 */

class AliasColumnsTable(private val resultColumns: RoomResultColumns) : SqlTable {
  override val name get() = null
  override val definingElement get() = resultColumns
  override val isView: Boolean get() = true

  override fun processColumns(processor: Processor<SqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>): Boolean {
    val resultColumns = resultColumns.resultColumnList

    for (resultColumn in resultColumns) {
      if (resultColumn.columnAliasName != null) {
        val sqlColumn = computeSqlColumn(resultColumn, sqlTablesInProcess)
        if (sqlColumn != null && !processor.process(sqlColumn)) return false
      }
    }

    return true
  }
}
