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

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Processor
import com.intellij.util.containers.ContainerUtil

typealias PsiClassPointer = SmartPsiElementPointer<out PsiClass>
typealias PsiFieldPointer = SmartPsiElementPointer<out PsiField>

data class RoomDatabase(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** Classes mentioned in the `entities` annotation parameter. These may not actually be `@Entities` if the code is wrong.  */
  val entities: Set<PsiClassPointer>
)

data class Entity(
  /** Annotated class. */
  val psiClass: PsiClassPointer,

  /** Name of the table: take from the class name or the annotation parameter. */
  override val name: String,

  /**
   * [PsiElement] that determines the table name and should be the destination of references from SQL.
   *
   * This can be either the class itself or the annotation element.
   */
  val nameElement: PsiElementPointer = psiClass,

  /** Columns present in the table representing this entity. */
  val columns: Set<EntityColumn> = emptySet()
) : SqlTable {
  override fun processColumns(processor: Processor<SqlColumn>) = ContainerUtil.process(columns, processor)
  override val definingElement: PsiElement get() = psiClass.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
  override val isView: Boolean get() = false
}

data class EntityColumn(
  /** Field that defines this column. */
  val psiField: PsiFieldPointer,

  /** Effective name of the column, either taken from the field or from `@ColumnInfo`. */
  override val name: String,

  /** The [PsiElement] that defines the column name. */
  val nameElement: PsiElementPointer = psiField
) : SqlColumn {
  override val type: SqlType? get() = psiField.element?.type?.presentableText?.let(::JavaFieldSqlType)
  override val definingElement: PsiElement get() = psiField.element!!
  override val resolveTo: PsiElement get() = nameElement.element!!
}

data class Dao(val psiClass: PsiClassPointer)

/**
 * Schema defined using Room annotations in Java/Kotlin code.
 */
data class RoomSchema(
  val databases: Set<RoomDatabase>,
  val entities: Set<Entity>,
  val daos: Set<Dao>
) {
  fun findEntity(psiClass: PsiClass) = entities.find { it.psiClass.element == psiClass }
}
