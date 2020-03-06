/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.Disposable

/**
 * An interface providing access to variables available in the specific scope.
 */
interface PsVariablesScope : PsKeyedModelCollection<String, PsVariable> {
  /**
   * The name of the variables scope.
   */
  val name: String

  /**
   * The title of the variables scope as it should appear in the UI.
   */
  val title: String

  /**
   * Returns a list of variables available in the scope which are suitable for use with
   * [property].
   */
  fun <ValueT : Any> getAvailableVariablesFor(property: ModelPropertyContext<ValueT>): List<Annotated<ParsedValue.Set.Parsed<ValueT>>>

  /**
   * Returns the specific variable scopes (including this one) from which this the list of variables available in this scope is composed.
   */
  fun getVariableScopes(): List<PsVariablesScope>

  /**
   * Returns a new not conflicting name for a new variable in this scope based on the [preferredName].
   */
  fun getNewVariableName(preferredName: String) : String

  fun getVariable(name: String): PsVariable?

  fun getOrCreateVariable(name: String): PsVariable

  fun addNewVariable(name: String): PsVariable

  fun addNewListVariable(name: String): PsVariable

  fun addNewMapVariable(name: String): PsVariable

  fun removeVariable(name: String)

  val model: PsModel

  object NONE : PsVariablesScope {
    override val name: String = ""
    override val title: String = ""
    override val model: PsModel get() = throw UnsupportedOperationException()
    override val items: Collection<PsVariable> = listOf()
    override val entries: Map<String, PsVariable> = mapOf()
    override fun findElement(key: String): PsVariable? = null
    override fun <ValueT : Any> getAvailableVariablesFor(
      property: ModelPropertyContext<ValueT>): List<Annotated<ParsedValue.Set.Parsed<ValueT>>> = listOf()

    override fun getVariableScopes(): List<PsVariablesScope> = listOf()
    override fun getVariable(name: String): PsVariable? = null
    override fun getNewVariableName(preferredName: String): String = throw UnsupportedOperationException()
    override fun getOrCreateVariable(name: String): PsVariable = throw UnsupportedOperationException()
    override fun addNewVariable(name: String): PsVariable = throw UnsupportedOperationException()
    override fun addNewListVariable(name: String): PsVariable = throw UnsupportedOperationException()
    override fun addNewMapVariable(name: String): PsVariable = throw UnsupportedOperationException()
    override fun removeVariable(name: String) = throw UnsupportedOperationException()
    override fun onChange(disposable: Disposable, listener: () -> Unit) = Unit
  }
}

