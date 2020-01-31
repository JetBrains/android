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

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Processor

typealias PsiElementPointer = SmartPsiElementPointer<out PsiElement>

/**
 * All names under which the primary key of a regular table can be referenced.
 *
 * See [https://sqlite.org/lang_createtable.html#rowid]
 */
val PRIMARY_KEY_NAMES = setOf("rowid", "oid", "_rowid_")

/**
 * All names under which the primary key of a full-text search table can be referenced.
 *
 * See [https://www.sqlite.org/fts3.html]
 */
val PRIMARY_KEY_NAMES_FOR_FTS = PRIMARY_KEY_NAMES + "docid"

/**
 * Describes a bind parameter available in a query, e.g. in `select * from t where id = :id`.
 */
data class BindParameter(val name: String, val definingElement: PsiElement?)

/** Defines common properties for parts of a SQL schema. */
interface AndroidSqlDefinition {
  /** Name of the entity being defined, if known. */
  val name: String?

  /** [PsiElement] that "created" this definition. */
  val definingElement: PsiElement

  /**
   * [PsiElement] that determines the name of this entity.
   *
   * This may be the same as [definingElement] or an annotation element.
   */
  val resolveTo: PsiElement get() = definingElement
}

/** Describes a SQL table. */
interface AndroidSqlTable : AndroidSqlDefinition {
  /**
   * Runs the [processor] on all [AndroidSqlColumn]s present in this [AndroidSqlTable].
   *
   * @see [AndroidSqlColumnPsiReference.resolveColumn] for [sqlTablesInProcess]
   */
  fun processColumns(processor: Processor<AndroidSqlColumn>, sqlTablesInProcess: MutableSet<PsiElement>): Boolean

  /** Whether this table is a view or an inner query, which means it cannot be modified directly. */
  val isView: Boolean
}

/**
 * [AndroidSqlTable] implementation used for aliases defined in SQL.
 *
 * [name] and [resolveTo] are overridden to point to the alias definition, but all other information comes from the original table.
 */
class AliasedTable(
  val delegate: AndroidSqlTable,
  override val name: String?,
  override val resolveTo: PsiElement
) : AndroidSqlTable by delegate


/** Describes a SQL column. */
interface AndroidSqlColumn : AndroidSqlDefinition {
  /** Type of the column, if known. */
  val type: SqlType?

  /**
   * Other names the column can be referred to. This can be the case with e.g 'rowid' column,
   * see https://sqlite.org/lang_createtable.html#rowid
   */
  val alternativeNames: Set<String> get() = emptySet()

  /** Whether the column is a primary key. */
  val isPrimaryKey: Boolean get() = false

  /** Whether this column is implicitly defined by SQLite, e.g. rowid. Such columns are skipped from code completion. */
  val isImplicit: Boolean get() = false
}

/**
 * [AndroidSqlColumn] implementation used for aliases defined in SQL.
 *
 * [name] and [resolveTo] are overridden to point to the alias definition, but all other information comes from the original column.
 */
class AliasedColumn(
  val delegate: AndroidSqlColumn,
  override val name: String,
  override val resolveTo: PsiElement
) : AndroidSqlColumn by delegate

/**
 * Represents columns of sub-queries, defined by arbitrary expressions, e.g. `select 2+2 from foo`.
 *
 * [ExprColumn] doesn't define [name], but if an alias was present in the sub-query, [ExprColumn] will be wrapped in an [AliasedColumn] to
 * provide the name.
 */
class ExprColumn(override val definingElement: PsiElement) : AndroidSqlColumn {
  override val name: String? get() = null
  override val type: SqlType? get() = null
}
