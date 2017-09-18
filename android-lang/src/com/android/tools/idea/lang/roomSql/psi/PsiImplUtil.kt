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

@file:JvmName("PsiImplUtil")

package com.android.tools.idea.lang.roomSql.psi

import com.android.tools.idea.lang.roomSql.RoomColumnPsiReference
import com.android.tools.idea.lang.roomSql.RoomTablePsiReference
import com.android.tools.idea.lang.roomSql.SingleTableContext
import com.android.tools.idea.lang.roomSql.SqlContext
import com.intellij.psi.PsiReference
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker


fun getReference(table: RoomTableName): PsiReference? = RoomTablePsiReference(table)

fun getReference(column: RoomColumnName): PsiReference? = RoomColumnPsiReference(column)

/**
 * Computes the [SqlContext] to use when resolving references inside a given select statement subtree.
 *
 * The context is cached in the select statement PSI node and invalidated when the Java structure is changed (potentially changing the
 * schema) or the SQL gets modified.
 */
fun getSqlContext(selectQuery: RoomSelectStmt): SqlContext? = CachedValuesManager.getCachedValue(selectQuery) {
  val context = when {
    selectQuery.tableOrSubqueryList.size == 1 -> {
      // Simplest case: `select foo from bar`.
      selectQuery.tableOrSubqueryList.singleOrNull()?.tableName?.let(::SingleTableContext)
    }
    else -> {
      // The query is too complicated for us to understand for now.
      null
    }
  }

  CachedValueProvider.Result(context, selectQuery, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT)
}
