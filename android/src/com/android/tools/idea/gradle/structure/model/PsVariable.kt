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
import com.android.tools.idea.gradle.structure.model.meta.GradleModelCoreProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import java.lang.IllegalStateException

/**
 * Model for handling Gradle properties in the Project Structure Dialog
 */
class PsVariable(
  private val property: GradlePropertyModel,
  private val resolvedProperty: ResolvedPropertyModel,
  val model: PsModel,
  val scopePsVariables: PsVariablesScope) {
  val valueType = property.valueType
  val resolvedValueType = resolvedProperty.valueType

  fun <T> getUnresolvedValue(type: TypeReference<T>): T? {
    return property.getRawValue(type)
  }

  fun <T> getResolvedValue(type: TypeReference<T>): T? {
    return resolvedProperty.getValue(type)
  }

  fun setValue(aValue: Any) {
    if (property.valueType == GradlePropertyModel.ValueType.BOOLEAN) {
      property.setValue((aValue as String).toBoolean())
    } else {
      property.setValue(aValue)
    }
    model.isModified = true
  }

  fun delete() {
    property.delete()
    model.isModified = true
  }

  fun setName(newName: String) {
    property.rename(newName)
    model.isModified = true
  }

  fun getName() = property.name

  fun addListValue(value: String): PsVariable {
    if (valueType != GradlePropertyModel.ValueType.LIST) {
      throw IllegalStateException("addListValue can only be called for list variables")
    }

    val listValue = property.addListValue()
    listValue.setValue(value)
    model.isModified = true
    return PsVariable(listValue, listValue.resolve(), model, scopePsVariables)
  }

  fun addMapValue(key: String): PsVariable? {
    if (valueType != GradlePropertyModel.ValueType.MAP) {
      throw IllegalStateException("addMapValue can only be called for map variables")
    }

    val mapValue = property.getMapValue(key)
    if (mapValue.psiElement != null) {
      return null
    }
    return PsVariable(mapValue, mapValue.resolve(), model, scopePsVariables)
  }

  fun getDependencies(): List<GradlePropertyModel> = property.dependencies

  /**
   * Binds a new property to the underlying Gradle property using the binding configuration from the [prototype].
   */
  @Suppress("UNCHECKED_CAST")
  fun <T : Any, PropertyCoreT : ModelPropertyCore<T>> bindNewPropertyAs(prototype: PropertyCoreT): PropertyCoreT? =
    // Note: the as? test is only to test whether the interface is implemented.
    // If it is, the generic type arguments will match.
    (prototype as? GradleModelCoreProperty<T, PropertyCoreT>)?.rebind(resolvedProperty) { model.isModified = true }
}
