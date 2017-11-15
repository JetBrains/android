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

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Processor

typealias PsiElementPointer = SmartPsiElementPointer<out PsiElement>

/** Defines common properties for parts of a SQL schema. */
interface SqlDefinition {
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

interface SqlTable : SqlDefinition {
  fun processColumns(processor: Processor<SqlColumn>): Boolean
  val isView: Boolean
}

interface SqlColumn : SqlDefinition {
  val type: SqlType?
}

class AliasedTable(
    val delegate: SqlTable,
    override val name: String?,
    override val resolveTo: PsiElement
) : SqlTable by delegate

class AliasedColumn(
    val delegate: SqlColumn,
    override val name: String,
    override val resolveTo: PsiElement
) : SqlColumn by delegate

class ExprColumn(override val definingElement: PsiElement) : SqlColumn {
  override val name get() = null
  override val type get() = null
}

interface SqlType {
  /** To be used in completion. */
  val typeName: String?
}

/**
 * A [SqlType] that just uses the name of the underlying java field.
 *
 * TODO: stop using this and track the effective SQL types instead.
 */
class JavaFieldSqlType(override val typeName: String) : SqlType

