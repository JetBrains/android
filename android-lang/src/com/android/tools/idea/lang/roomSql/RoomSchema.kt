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

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.SmartPsiElementPointer

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
    override val nameElement: PsiElementPointer = psiClass,

    /** Columns present in the table representing this entity. */
    override val columns: Set<EntityColumn> = emptySet()
) : SqlTable {
  override val element get() = psiClass
}

data class EntityColumn(
    /** Field that defines this column. */
    val psiField: PsiFieldPointer,

    /** Effective name of the column, either taken from the field or from `@ColumnInfo`. */
    override val name: String,

    /** The [PsiElement] that defines the column name. */
    override val nameElement: PsiElementPointer = psiField
) : SqlColumn {
  override val element get() = psiField
}

data class Dao(val psiClass: PsiClassPointer)

/**
 * [SqlContext] defined using Room annotations in Java/Kotlin code.
 *
 * This is an edge case and a fallback: if we don't understand the query in enough detail, we offer/accept all defined table and column
 * names.
 *
 * @see chooseContext
 */
data class RoomSchema(
    val databases: Set<RoomDatabase>,
    val entities: Set<Entity>,
    val daos: Set<Dao>
) : SqlContext {
  override val availableTables: Collection<SqlTable> get() = entities
  override val columns: Collection<SqlColumn> get() = entities.flatMap { it.columns }

  override val isSound: Boolean get() {
    // RoomSchema is the fallback context, it contains all tables and columns whether or not they were selected from or not. It also has no
    // understanding of temporary table and columns from e.g. subqueries.
    return false
  }

  fun findEntity(psiClass: PsiClass) = entities.find { it.psiClass.element == psiClass }
}
