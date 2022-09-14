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

import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase
import com.android.tools.idea.gradle.structure.model.helpers.formatAny
import com.android.tools.idea.gradle.structure.model.helpers.parseAny
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.GradleModelCoreProperty
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ListProperty
import com.android.tools.idea.gradle.structure.model.meta.MapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.asAny
import com.android.tools.idea.gradle.structure.model.meta.listProperty
import com.android.tools.idea.gradle.structure.model.meta.mapProperty
import com.android.tools.idea.gradle.structure.model.meta.property
import com.android.tools.idea.gradle.structure.model.meta.setParsedValue
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.intellij.openapi.diagnostic.Logger

/**
 * Model for handling Gradle properties in the Project Structure Dialog
 */
class PsVariable(
  override val parent: PsModel,
  val scopePsVariables: PsVariablesScope,
  val refreshCollection: () -> Unit
) : PsChildModel() {

  fun init(property: GradlePropertyModel) {
    this.property = property
    this.resolvedProperty = property.resolve()
    myListItems?.refresh()
    myMapEntries?.refresh()
    pendingListItemContainer = null
  }

  private fun initNewListItem(property: ResolvedPropertyModel) {
    pendingListItemContainer = property
  }

  private var property: GradlePropertyModel? = null
  private var resolvedProperty: ResolvedPropertyModel? = null
  private var myListItems: ListVariableEntries? = null
  private var pendingListItemContainer: ResolvedPropertyModel? = null
  val listItems: PsKeyedModelCollection<Int, PsVariable> = myListItems ?: ListVariableEntries(this).also { myListItems = it }
  val isList: Boolean get() = property?.valueType == GradlePropertyModel.ValueType.LIST
  private var myMapEntries: MapVariableEntries? = null
  val mapEntries: PsKeyedModelCollection<String, PsVariable> = myMapEntries ?: MapVariableEntries(this).also {myMapEntries = it }
  val isMap: Boolean get() = property?.valueType == GradlePropertyModel.ValueType.MAP
  override val name: String get() = property?.name ?: ""
  override val isDeclared: Boolean = true
  var value by Descriptors.variableValue

  fun convertToEmptyList() = resolvedProperty!!.convertToEmptyList()
  fun convertToEmptyMap() = resolvedProperty!!.convertToEmptyMap()

  fun delete() {
    property!!.delete()
    refreshCollection()
    parent.isModified = true
  }

  fun setName(newName: String) {
    property!!.rename(newName)
    refreshCollection()
    parent.isModified = true
  }

  fun addListValue(value: ParsedValue<Any>): PsVariable {
    if (!isList) throw IllegalStateException("addListValue can only be called for list variables")
    return if (value === ParsedValue.NotSet) {
      PsVariable(this, scopePsVariables, { myListItems?.refresh() }).also { it.initNewListItem(resolvedProperty!!) }
    }
    else {
      val listValue = this.property!!.addListValue().resolve()
      listValue.setParsedValue({ setValue(it) }, {}, value)
      parent.isModified = true
      myListItems?.refresh()
      listItems.findElement(listItems.size - 1)!!
    }
  }

  fun addMapValue(key: String): PsVariable? {
    if (!isMap) throw IllegalStateException("addMapValue can only be called for map variables")
    val mapValue = property!!.getMapValue(key)
    if (mapValue.psiElement != null) {
      return null
    }
    mapValue.setValue("")
    myMapEntries?.refresh()
    return mapEntries.findElement(key)!!
  }

  /**
   * Binds a new property to the underlying Gradle property using the binding configuration from the [prototype].
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any, PropertyCoreT : ModelPropertyCore<T>> bindNewPropertyAs(prototype: PropertyCoreT): PropertyCoreT? =
  // Note: the as? test is only to test whether the interface is implemented.
  // If it is, the generic type arguments will match.
    (prototype as? GradleModelCoreProperty<T, PropertyCoreT>)?.rebind(resolvedProperty!!) { block -> block() ; parent.isModified = true }

  object Descriptors : ModelDescriptor<PsVariable, Nothing, ResolvedPropertyModel> {
    override fun getResolved(model: PsVariable): Nothing? = null

    override fun getParsed(model: PsVariable): ResolvedPropertyModel? = model.resolvedProperty

    override fun prepareForModification(model: PsVariable) {
      model.pendingListItemContainer?.let {
        val itemProperty = it.addListValue()
        model.property = itemProperty
        model.resolvedProperty = itemProperty.resolve()
        model.pendingListItemContainer = null
        model.refreshCollection()
      }
      model.scopePsVariables.model.isModified = true
      model.myListItems?.refresh()
      model.myMapEntries?.refresh()
    }

    override fun setModified(model: PsVariable) = Unit

    val variableValue: SimpleProperty<PsVariable, Any> = property(
      "Value",
      defaultValueGetter = null,
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny,
      formatter = ::formatAny,
      knownValuesGetter = ::variableKnownValues,
      variableMatchingStrategy = VariableMatchingStrategy.BY_TYPE
    )

    val variableListValue: ListProperty<PsVariable, Any> = listProperty(
      "Value",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny,
      knownValuesGetter = ::variableKnownValues,
      variableMatchingStrategy = VariableMatchingStrategy.BY_TYPE
    )

    val variableMapValue: MapProperty<PsVariable, Any> = mapProperty(
      "Value",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny,
      knownValuesGetter = ::variableKnownValues,
      variableMatchingStrategy = VariableMatchingStrategy.BY_TYPE
    )

    private fun variableKnownValues(variable: PsVariable): ListenableFuture<List<ValueDescriptor<Any>>> {
      val potentiallyReferringModels = variable.scopePsVariables.model.descriptor.enumerateContainedModels()
      val collector = variable.ReferenceContextCollector()
      potentiallyReferringModels.forEach { it.descriptor.enumerateProperties(collector) }
      return Futures
        .successfulAsList(collector.collectedReferences.map { it.getKnownValues() })
        .transform(directExecutor()) { it.combineKnownValues() }
    }
  }

  private inner class ReferenceContextCollector : PsModelDescriptor.PropertyReceiver {
    val collectedReferences = mutableListOf<ModelPropertyContext<out Any>>()
    override fun <T : PsModel> receive(model: T, property: ModelProperty<T, *, *, *>) {
      try {
        val value = property.getValue(model, ::FAKE_PROPERTY)
        if (value !is ParsedValue.Set.Parsed || value.dslText !is DslText.Reference) return
        val propertyCore = property.bind(model) as? GradleModelCoreProperty<*, *> ?: return
        var propertyModel: GradlePropertyModel = propertyCore.getParsedPropertyForRead()?.unresolvedModel ?: return
        val seen = mutableSetOf<GradlePropertyModel>()
        while (propertyModel.valueType == GradlePropertyModel.ValueType.REFERENCE && propertyModel.dependencies.isNotEmpty()) {
          if (!seen.add(propertyModel)) return
          propertyModel = propertyModel.dependencies[0]!!
          if (resolvedProperty?.fullyQualifiedName == propertyModel.fullyQualifiedName &&
              resolvedProperty?.gradleFile?.path == propertyModel.gradleFile.path) {
            collectedReferences.add(property.bindContext(model))
            return
          }
        }
      }
      catch (e: Exception) {
        LOG.warn(e)
      }
    }
  }

  class MapVariableEntries(variable: PsVariable) : PsCollectionBase<PsVariable, String, PsVariable>(variable) {
    init {
      refresh()
    }

    override fun getKeys(from: PsVariable): Set<String> =
      parent.property?.takeIf { it.valueType == GradlePropertyModel.ValueType.MAP }?.toMap()?.keys ?: setOf()

    override fun create(key: String): PsVariable = PsVariable(parent, parent.scopePsVariables, ::refresh)
    override fun update(key: String, model: PsVariable) = model.init(parent.property!!.getMapValue(key))
  }

  class ListVariableEntries(variable: PsVariable) : PsCollectionBase<PsVariable, Int, PsVariable>(variable) {
    init {
      refresh()
    }

    override fun getKeys(from: PsVariable): Set<Int> =
      parent.property?.takeIf { it.valueType == GradlePropertyModel.ValueType.LIST }?.toList()?.let { 0 until it.size }?.toSet() ?: setOf()

    override fun create(key: Int): PsVariable = PsVariable(parent, parent.scopePsVariables, ::refresh)
    override fun update(key: Int, model: PsVariable) = model.init(parent.property!!.getValue(GradlePropertyModel.LIST_TYPE)!![key])
  }
}

/**
 * Combines multiple [KnownValues] instances by intersecting non-empty sets of known-values.
 */
private fun <T : Any> Collection<KnownValues<out T>>.combineKnownValues() =
  map { it.literals.toSet() }
    .fold(setOf<ValueDescriptor<T>>()) { acc, v ->
      when {
        acc.isEmpty() -> v
        v.isEmpty() -> acc
        else -> acc intersect v
      }
    }
    .toList()

private val LOG = Logger.getInstance(PsVariable::class.java)
private val FAKE_PROPERTY: Nothing? = null
