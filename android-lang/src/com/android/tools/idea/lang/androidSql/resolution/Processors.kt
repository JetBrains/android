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
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor

/**
 * [Processor] that finds a table with a given name.
 */
class FindTableByNameProcessor(private val nameToLookFor: String) : CommonProcessors.FindProcessor<AndroidSqlTable>() {
  override fun accept(t: AndroidSqlTable): Boolean = nameToLookFor.equals(t.name, ignoreCase = true)
}

class FindColumnByNameProcessor(private val nameToLookFor: String) : CommonProcessors.FindProcessor<AndroidSqlColumn>() {
  override fun accept(c: AndroidSqlColumn): Boolean {
    return nameToLookFor.equals(c.name, ignoreCase = true) || c.alternativeNames.any{ it.equals(nameToLookFor, ignoreCase = true) }
  }
}

/**
 * [Processor] that records all available tables/columns that have a name.
 */
class CollectUniqueNamesProcessor<T : AndroidSqlDefinition> : Processor<T> {
  val map: HashMap<String, T> = hashMapOf()
  val result: Collection<T> get() = map.values

  override fun process(t: T): Boolean {
    val name = t.name
    if (name != null) map.putIfAbsent(name, t)
    return true
  }
}

/**
 * Runs a [delegate] [Processor] on every [AndroidSqlColumn] of every processed [AndroidSqlTable].
 *
 *  @see AndroidSqlColumnPsiReference for [sqlTablesInProcess] explanation
 */
class AllColumnsProcessor(
  private val delegate: Processor<AndroidSqlColumn>,
  private val sqlTablesInProcess: MutableSet<PsiElement>
) : Processor<AndroidSqlTable> {
  var tablesProcessed = 0

  override fun process(t: AndroidSqlTable): Boolean {
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

class IgnoreViewsProcessor(private val delegate: Processor<AndroidSqlTable>) : Processor<AndroidSqlTable> {
  override fun process(t: AndroidSqlTable): Boolean = t.isView || delegate.process(t)
}

