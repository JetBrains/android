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
package com.android.tools.idea.lang.androidSql

import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.BindParameter
import com.android.tools.idea.lang.androidSql.resolution.JavaFieldSqlType
import com.android.tools.idea.lang.androidSql.resolution.SqlType
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil

fun PsiFile.setTestSqlSchema(block: AndroidSqlTestSchema.() -> Unit): AndroidSqlTestSchema {
  val schema = AndroidSqlTestSchema(this).apply(block)
  virtualFile.putUserData(AndroidSqlTestContext.TEST_SQLITE_SCHEMA_KEY, schema)
  return schema
}

fun PsiFile.setTestSqlSchema(schema: AndroidSqlTestSchema): AndroidSqlTestSchema {
  virtualFile.putUserData(AndroidSqlTestContext.TEST_SQLITE_SCHEMA_KEY, schema)
  return schema
}

fun androidSqlTestSchema(psiFile: PsiFile, block: AndroidSqlTestSchema.() -> Unit) = AndroidSqlTestSchema(psiFile).apply(block)

class AndroidSqlTestSchema(private val psiFile: PsiFile, var tables: MutableList<AndroidSqlTestTable> = mutableListOf()) {
  fun table(table: AndroidSqlTestTable.() -> Unit) {
    tables.add(AndroidSqlTestTable(psiFile).apply(table))
  }

  fun view(table: AndroidSqlTestTable.() -> Unit) {
    tables.add(AndroidSqlTestTable(psiFile, isView = true).apply(table))
  }

  fun getTable(tableName: String): AndroidSqlTestTable = tables.find { it.name == tableName }!!
}

class AndroidSqlTestTable(override val definingElement: PsiFile, override var isView: Boolean = false) : AndroidSqlTable {
  override var name: String = ""
  var columns: MutableList<AndroidSqlTestColumn> = mutableListOf()

  fun column(column: AndroidSqlTestColumn.() -> Unit) {
    columns.add(AndroidSqlTestColumn(definingElement = definingElement).apply(column))
  }

  override fun processColumns(processor: Processor<AndroidSqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>): Boolean {
    return ContainerUtil.process(columns, processor)
  }

  fun getColumn(columnName: String): AndroidSqlTestColumn = columns.find { it.name == columnName }!!
}

data class AndroidSqlTestColumn(override val definingElement: PsiFile) : AndroidSqlColumn {
  override var name: String = ""
  override var type: SqlType = JavaFieldSqlType("")
}

data class AndroidSqlTestContext(private val schema: AndroidSqlTestSchema) : AndroidSqlContext {
  override val bindParameters: Map<String, BindParameter> = emptyMap()

  companion object {
    // Schema should be added to VirtualFile of PsiFile, otherwise it would be lost during code completion.
    val TEST_SQLITE_SCHEMA_KEY = Key.create<AndroidSqlTestSchema>("TEST_SQLITE_SCHEMA_KEY")
  }

  class Provider : AndroidSqlContext.Provider {
    override fun getContext(query: AndroidSqlFile): AndroidSqlContext? {
      val schema = query.originalFile.virtualFile?.getUserData(TEST_SQLITE_SCHEMA_KEY) ?: return null
      return AndroidSqlTestContext(schema)
    }
  }

  override fun processTables(processor: Processor<AndroidSqlTable>): Boolean {
    return ContainerUtil.process(schema.tables, processor)
  }
}
