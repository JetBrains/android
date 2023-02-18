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
package com.android.tools.idea.sqlite.sqlLanguage

import com.android.tools.idea.lang.androidSql.AndroidSqlContext
import com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlColumn
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.BindParameter
import com.android.tools.idea.lang.androidSql.resolution.SqlType
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteTable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil

/**
 * An [AndroidSqlContext] based on an [SqliteSchema] stored in the user data of [query]'s virtual
 * file.
 *
 * Meant to be used in the database inspector UI which stores the schema in the temporary files
 * created for ad-hoc queries.
 */
class SqliteSchemaContext(private val schema: SqliteSchema, private val query: AndroidSqlFile) :
  AndroidSqlContext {
  companion object {
    val SQLITE_SCHEMA_KEY = Key.create<SqliteSchema>("SQLITE_SCHEMA_KEY")
  }

  class Provider : AndroidSqlContext.Provider {
    override fun getContext(query: AndroidSqlFile): AndroidSqlContext? {
      val schema = query.originalFile.virtualFile?.getUserData(SQLITE_SCHEMA_KEY) ?: return null
      return SqliteSchemaContext(schema, query)
    }
  }

  override val bindParameters: Map<String, BindParameter> = emptyMap()

  override fun processTables(processor: Processor<AndroidSqlTable>): Boolean {
    return ContainerUtil.process(schema.tables.map { it.convertToSqlTable(query) }, processor)
  }
}

/** Element that columns and tables based on [SqliteSchema] resolve to. */
class AndroidSqlFakePsiElement(
  private val query: PsiFile,
  private val _name: String,
  val typeDescription: String?
) : FakePsiElement() {
  override fun getParent() = query
  override fun canNavigate(): Boolean = false
  override fun getContainingFile(): PsiFile? = null
  override fun getName(): String = _name
}

/** Type that corresponds to [com.android.tools.idea.sqlite.model.SqliteAffinity]. */
data class SqliteSchemaSqlType(override val typeName: String) : SqlType

data class SqliteSchemaColumn(
  override val name: String,
  val query: PsiFile,
  override val type: SqliteSchemaSqlType
) : AndroidSqlColumn {
  override val definingElement = AndroidSqlFakePsiElement(query, name, type.typeName)
}

/** Converts [SqliteTable] to [AndroidSqlTable]. */
fun SqliteTable.convertToSqlTable(query: PsiFile): AndroidSqlTable {
  val androidSqlColumns =
    columns.map { SqliteSchemaColumn(it.name, query, SqliteSchemaSqlType(it.affinity.name)) }
  val tableName = name
  val isView = isView

  return object : AndroidSqlTable {
    override fun processColumns(
      processor: Processor<AndroidSqlColumn>,
      sqlTablesInProcess: MutableSet<PsiElement>
    ): Boolean {
      return ContainerUtil.process(androidSqlColumns, processor)
    }

    override val isView: Boolean
      get() = isView
    override val name: String
      get() = tableName
    override val definingElement = AndroidSqlFakePsiElement(query, name, "")
  }
}
