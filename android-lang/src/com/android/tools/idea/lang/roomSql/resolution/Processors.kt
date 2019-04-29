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
import com.intellij.psi.PsiManager
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor

/**
 * [Processor] that finds a table/column with a given name.
 */
class FindByNameProcessor<T : SqlDefinition>(private val nameToLookFor: String) : CommonProcessors.FindProcessor<T>() {
  override fun accept(t: T): Boolean = nameToLookFor.equals(t.name, ignoreCase = true)
}

/**
 * [Processor] that records all available tables/columns that have a name.
 */
class CollectUniqueNamesProcessor<T : SqlDefinition> : Processor<T> {
  val map: HashMap<String, T> = hashMapOf()
  val result: Collection<T> get() = map.values

  override fun process(t: T): Boolean {
    val name = t.name
    if (name != null) map.putIfAbsent(name, t)
    return true
  }
}

/**
 * Runs a [delegate] [Processor] on every [SqlColumn] of every processed [SqlTable].
 *
 *  @see RoomColumnPsiReference for [sqlTablesInProcess] explanation
 */
class AllColumnsProcessor(
  private val delegate: Processor<SqlColumn>,
  private val sqlTablesInProcess: MutableSet<PsiElement>
) : Processor<SqlTable> {
  var tablesProcessed = 0

  override fun process(t: SqlTable): Boolean {
    if (sqlTablesInProcess.contains(t.definingElement)) {
      // We don't want to continue process if we found recursion.
      return false;
    }
    sqlTablesInProcess.add(t.definingElement)
    t.processColumns(delegate, sqlTablesInProcess)
    sqlTablesInProcess.remove(t.definingElement)
    tablesProcessed++
    return true
  }
}

class IgnoreViewsProcessor(private val delegate: Processor<SqlTable>) : Processor<SqlTable> {
  override fun process(t: SqlTable): Boolean = t.isView || delegate.process(t)
}

class IgnoreClassProcessor(private val toSkip: PsiClass, private val delegate: Processor<SqlTable>) : Processor<SqlTable> {
  private val psiManager: PsiManager = PsiManager.getInstance(toSkip.project)

  override fun process(t: SqlTable?): Boolean {
    val definingClass = (t as? RoomTable)?.psiClass?.element ?: return true
    // During code completion the two classes may not be equal, because the file being edited is copied for completion purposes. But they
    // are equivalent according to the PsiManager.
    return psiManager.areElementsEquivalent(definingClass, toSkip) || delegate.process(t)
  }
}
