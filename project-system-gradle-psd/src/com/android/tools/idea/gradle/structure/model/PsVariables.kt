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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.structure.model.android.PsMutableCollectionBase
import com.android.tools.idea.gradle.structure.model.meta.*
import com.google.common.annotations.VisibleForTesting

open class PsVariables (
  override val model: PsModel,
  override val name: String,
  override val title: String,
  private val parentScope: PsVariablesScope?
) : PsMutableCollectionBase<PsVariable, String, PsModel>(model), PsVariablesScope {
  init {
    refresh()
  }

  override fun getKeys(from: PsModel): Set<String> =
    getContainer(from)
      ?.properties
      ?.map { it.name }
      ?.toSet()
    ?: setOf()

  override fun create(key: String): PsVariable = PsVariable(model, this, ::refresh)
  override fun update(key: String, model: PsVariable) = model.init(getContainer(parent)!!.properties.first { it.name == key })

  override fun instantiateNew(key: String) {
    // TODO(solodkyy): Consider not initializing the variable and letting it hang around int the collection until the next refresh
    // or explicit initialization.
    getContainer(parent)!!.findProperty(key).setValue("")
  }

  override fun removeExisting(key: String) {
    getContainer(parent)!!.findProperty(key).delete()
  }

  override fun <ValueT : Any> getAvailableVariablesFor(
    property: ModelPropertyContext<ValueT>
  ): List<Annotated<ParsedValue.Set.Parsed<ValueT>>> =
  // TODO(solodkyy): Merge with variables available at the project level.
    getContainer(model)
      ?.inScopeProperties
      ?.map { it.key to it.value.resolve() }
      ?.flatMap {
        when (it.second.valueType) {
          GradlePropertyModel.ValueType.LIST -> listOf()
          GradlePropertyModel.ValueType.MAP ->
            it.second.getValue(GradlePropertyModel.MAP_TYPE)?.map { entry ->
              "${it.first}.${entry.key}" to entry.value
            }.orEmpty()
          else -> listOf(it)
        }
      }
      ?.mapNotNull { (name, resolvedProperty) ->
        resolvedProperty.getValue(GradlePropertyModel.OBJECT_TYPE)?.let { name to property.parse(it.toString()) }
      }
      ?.mapNotNull { (name, annotatedValue) ->
        when {
          (annotatedValue.value is ParsedValue.Set.Parsed && annotatedValue.annotation !is ValueAnnotation.Error) ->
            ParsedValue.Set.Parsed(annotatedValue.value.value, DslText.Reference(name)).annotateWith(annotatedValue.annotation)
          else -> null
        }
      } ?: listOf()

  override fun getVariableScopes(): List<PsVariablesScope> =
    parentScope?.getVariableScopes().orEmpty() + listOf<PsVariablesScope>(this as PsVariablesScope)

  override fun getNewVariableName(preferredName: String) =
    generateSequence(0, { it + 1 })
      .map { if (it == 0) preferredName else "$preferredName$it" }
      .first { getContainer(parent)!!.findProperty(it).valueType == GradlePropertyModel.ValueType.NONE }

  override fun getVariable(name: String): PsVariable? = findElement(name)

  override fun getOrCreateVariable(name: String): PsVariable = findElement(name) ?: addNewVariable(name)

  override fun addNewVariable(name: String): PsVariable = addNew(name)
  override fun addNewListVariable(name: String): PsVariable =
    batchChange {
      addNew(name).also { it.convertToEmptyList() }
    }

  override fun addNewMapVariable(name: String): PsVariable =
    batchChange {
      addNew(name).also { it.convertToEmptyMap() }
    }

  override fun removeVariable(name: String) = remove(name)

  @VisibleForTesting
  protected open fun getContainer(from: PsModel) =
    when (from) {
      is PsProject -> from.parsedModel.projectBuildModel?.ext()
      is PsModule -> from.parsedModel?.ext()
      is PsBuildScript -> from.parsedModel?.ext()
      is PsVersionCatalog -> from.parsedModel?.versions()
      else -> throw IllegalStateException()
    }
}
