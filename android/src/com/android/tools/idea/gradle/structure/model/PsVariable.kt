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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.structure.model.helpers.parseAny
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.projectsystem.transform
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import java.lang.IllegalStateException

/**
 * Model for handling Gradle properties in the Project Structure Dialog
 */
class PsVariable(
  private val property: GradlePropertyModel,
  override val parent: PsModel,
  val scopePsVariables: PsVariablesScope
) : PsChildModel() {
  private val resolvedProperty: ResolvedPropertyModel = property.resolve()

  override val name: String get() = property.name
  override val isDeclared: Boolean = true
  val valueType get() = property.valueType
  val resolvedValueType get() = resolvedProperty.valueType
  var value by Descriptors.variableValue

  fun <T> getUnresolvedValue(type: TypeReference<T>): T? {
    return property.getRawValue(type)
  }

  fun <T> getResolvedValue(type: TypeReference<T>): T? {
    return resolvedProperty.getValue(type)
  }

  fun convertToEmptyList() = resolvedProperty.convertToEmptyList()
  fun convertToEmptyMap() = resolvedProperty.convertToEmptyMap()

  fun setValue(aValue: Any) {
    if (property.valueType == GradlePropertyModel.ValueType.BOOLEAN) {
      property.setValue((aValue as String).toBoolean())
    } else {
      property.setValue(aValue)
    }
    parent.isModified = true
  }

  fun delete() {
    property.delete()
    parent.isModified = true
  }

  fun setName(newName: String) {
    property.rename(newName)
    parent.isModified = true
  }

  fun addListValue(value: String): PsVariable {
    if (valueType != GradlePropertyModel.ValueType.LIST) {
      throw IllegalStateException("addListValue can only be called for list variables")
    }

    val listValue = property.addListValue()
    listValue.setValue(value)
    parent.isModified = true
    return PsVariable(listValue, this, scopePsVariables)
  }

  fun addMapValue(key: String): PsVariable? {
    if (valueType != GradlePropertyModel.ValueType.MAP) {
      throw IllegalStateException("addMapValue can only be called for map variables")
    }

    val mapValue = property.getMapValue(key)
    if (mapValue.psiElement != null) {
      return null
    }
    return PsVariable(mapValue, this, scopePsVariables)
  }

  fun getDependencies(): List<GradlePropertyModel> = property.dependencies

  /**
   * Binds a new property to the underlying Gradle property using the binding configuration from the [prototype].
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any, PropertyCoreT : ModelPropertyCore<T>> bindNewPropertyAs(prototype: PropertyCoreT): PropertyCoreT? =
  // Note: the as? test is only to test whether the interface is implemented.
  // If it is, the generic type arguments will match.
    (prototype as? GradleModelCoreProperty<T, PropertyCoreT>)?.rebind(resolvedProperty) { parent.isModified = true }

  object Descriptors : ModelDescriptor<PsVariable, Nothing, ResolvedPropertyModel> {
    override fun getResolved(model: PsVariable): Nothing? = null

    override fun getParsed(model: PsVariable): ResolvedPropertyModel? = model.resolvedProperty

    override fun setModified(model: PsVariable) {
      model.scopePsVariables.model.isModified = true
    }

    val variableValue: SimpleProperty<PsVariable, Any> = property(
      "Value",
      defaultValueGetter = null,
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny,
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

    fun variableKnownValues(variable: PsVariable): ListenableFuture<List<ValueDescriptor<Any>>> {
      val potentiallyReferringModels = variable.scopePsVariables.model.descriptor.enumerateContainedModels()
      val collector = variable.ReferenceContextCollector()
      potentiallyReferringModels.forEach { it.descriptor.enumerateProperties(collector) }
      return Futures
        .successfulAsList(collector.collectedReferences.map { it.getKnownValues() })
        .transform { it.combineKnownValues() }
    }
  }

  private inner class ReferenceContextCollector : PsModelDescriptor.PropertyReceiver {
    val collectedReferences = mutableListOf<ModelPropertyContext<out Any>>()
    override fun <T : PsModel> receive(model: T, property: ModelProperty<T, *, *, *>) {
      try {
        val value = property.getValue(model, ::FAKE_PROPERTY)
        if (value !is ParsedValue.Set.Parsed || value.dslText !is DslText.Reference) return
        val propertyCore = property.bind(model) as? GradleModelCoreProperty<*, *> ?: return
        var propertyModel: GradlePropertyModel = propertyCore.getParsedProperty()?.unresolvedModel ?: return
        val seen = mutableSetOf<GradlePropertyModel>()
        while (propertyModel.valueType == GradlePropertyModel.ValueType.REFERENCE) {
          if (!seen.add(propertyModel)) return
          propertyModel = propertyModel.dependencies[0]!!
          if (resolvedProperty.fullyQualifiedName == propertyModel.fullyQualifiedName &&
              resolvedProperty.gradleFile.path == propertyModel.gradleFile.path) {
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
}

/**
 * Combines multiple [KnownValues] instances by intersecting non-empty sets of known-values.
 */
private fun <T> Collection<KnownValues<out T>>.combineKnownValues() =
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
