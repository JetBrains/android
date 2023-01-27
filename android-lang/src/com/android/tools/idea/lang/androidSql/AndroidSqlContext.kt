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
import com.android.tools.idea.lang.androidSql.resolution.AndroidSqlTable
import com.android.tools.idea.lang.androidSql.resolution.BindParameter
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.Processor
import kotlin.streams.asSequence

/**
 * Describes context in which a query ([com.android.tools.idea.lang.androidSql.psi.AndroidSqlFile]) will execute.
 */
interface AndroidSqlContext {

  /** Runs the [processor] on all [AndroidSqlTable]s in the context schema. */
  fun processTables(processor: Processor<AndroidSqlTable>): Boolean

  /** Bind parameters available in the query. */
  val bindParameters: Map<String, BindParameter>

  /** Extension point for providing [AndroidSqlContext] for a given query. */
  interface Provider {
    /** Returns context for a query, if this provider wants to handle it. */
    fun getContext(query: AndroidSqlFile): AndroidSqlContext?

    companion object {
      val EP_NAME: ExtensionPointName<AndroidSqlContext.Provider> = ExtensionPointName.create(
        "com.android.tools.idea.lang.androidSql.contextProvider");
    }
  }
}

/**
 * Finds the [AndroidSqlContext] applicable to this [AndroidSqlFile], if known.
 */
val AndroidSqlFile.sqlContext: AndroidSqlContext? get() {
  return AndroidSqlContext.Provider.EP_NAME.extensionList.asSequence().mapNotNull { it.getContext(this) }.firstOrNull()
}