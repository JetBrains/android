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

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlColumnDefinitionName
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlWithClauseTable
import com.intellij.psi.PsiElement
import com.intellij.util.Processor

class WithClauseTable(withClauseTable: AndroidSqlWithClauseTable) : AndroidSqlTable {
  private val tableDefinition = withClauseTable.withClauseTableDef
  override val name get() = tableDefinition.tableDefinitionName.nameAsString
  override val definingElement get() = tableDefinition.tableDefinitionName
  override val isView: Boolean get() = true

  override fun processColumns(processor: Processor<AndroidSqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>): Boolean {
    for (columnDefinition in tableDefinition.columnDefinitionNameList) {
      if (!processor.process(DefinedColumn(columnDefinition))) return false
    }

    return true
  }

  private class DefinedColumn(override val definingElement: AndroidSqlColumnDefinitionName) : AndroidSqlColumn {
    override val name get() = definingElement.nameAsString
    override val type: SqlType? get() = null
  }
}
