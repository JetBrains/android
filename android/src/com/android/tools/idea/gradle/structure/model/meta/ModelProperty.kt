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
package com.android.tools.idea.gradle.structure.model.meta

import com.google.common.util.concurrent.ListenableFuture
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Core methods of a UI property descriptor manipulating parsed values.
 */
interface ModelPropertyParsedCore<in ModelT, PropertyT : Any> {
  fun getParsedValue(model: ModelT): ParsedValue<PropertyT>
  fun setParsedValue(model: ModelT, value: ParsedValue<PropertyT>)
}

/**
 * Core methods of a UI property descriptor manipulating resolved values.
 */
interface ModelPropertyResolvedCore<in ModelT, out PropertyT : Any> {
  fun getResolvedValue(model: ModelT): ResolvedValue<PropertyT>
}

/**
 * A UI core descriptor of a property of a model of type [ModelT].
 */
interface ModelPropertyCore<in ModelT, PropertyT : Any> :
  ModelPropertyParsedCore<ModelT, PropertyT>,
  ModelPropertyResolvedCore<ModelT, PropertyT>

fun <ModelT, PropertyT : Any> ModelPropertyCore<ModelT, PropertyT>.getValue(model: ModelT): PropertyValue<PropertyT> =
  PropertyValue(parsedValue = getParsedValue(model), resolved = getResolvedValue(model))

/**
 * A UI descriptor of a property of a model of type [ModelT].
 */
interface ModelProperty<in ModelT, PropertyT : Any> :
  ModelPropertyCore<ModelT, PropertyT>,
  ReadWriteProperty<ModelT, ParsedValue<PropertyT>> {
  /**
   * A property description as it should appear in the UI.
   */
  val description: String

  fun getDefaultValue(model: ModelT): PropertyT?
}

@Suppress("AddVarianceModifier")  // PSQ erroneously reports AddVarianceModifier on ValueT here.
interface ModelPropertyContext<in ModelT, ValueT : Any> {
  /**
   * Parses the text representation of type [ValueT].
   *
   * This is up to the parser to decide whether [value] is valid, invalid or is a DSL expression.
   */
  fun parse(value: String): ParsedValue<ValueT>

  /**
   * Returns a list of well-known values (constants) with their short human-readable descriptions that are applicable to the property.
   */
  fun getKnownValues(model: ModelT): ListenableFuture<List<ValueDescriptor<ValueT>>>
}

/**
 * A UI descriptor of a simple-typed property.
 *
 * The simple-types property is a property whose value can be easily represented in the UI as text.
 */
interface ModelSimpleProperty<in ModelT, PropertyT : Any> :
  ModelProperty<ModelT, PropertyT>,
  ModelPropertyContext<ModelT, PropertyT>
typealias SimpleProperty<ModelT, PropertyT> = ModelSimpleProperty<ModelT, PropertyT>
/**
 * A UI descriptor of a collection property.
 */
interface ModelCollectionProperty<in ModelT, CollectionT : Any, ValueT : Any>
  : ModelProperty<ModelT, CollectionT>,
    ModelPropertyContext<ModelT, ValueT>

/**
 * A UI descriptor of a list property.
 */
interface ModelListProperty<in ModelT, ValueT : Any> :
  ModelCollectionProperty<ModelT, List<ValueT>, ValueT> {
  fun getEditableValues(model: ModelT): List<ModelPropertyCore<Unit, ValueT>>
  fun addItem(model: ModelT, index: Int): ModelPropertyCore<Unit, ValueT>
  fun deleteItem(model: ModelT, index: Int)
}
typealias ListProperty<ModelT, PropertyT> = ModelListProperty<ModelT, PropertyT>

/**
 * A UI descriptor of a map property.
 */
interface ModelMapProperty<in ModelT, ValueT : Any> :
  ModelCollectionProperty<ModelT, Map<String, ValueT>, ValueT> {
  fun getEditableValues(model: ModelT): Map<String, ModelPropertyCore<Unit, ValueT>>
  fun addEntry(model: ModelT, key: String): ModelPropertyCore<Unit, ValueT>
  fun deleteEntry(model: ModelT, key: String)
  fun changeEntryKey(model: ModelT, old: String, new: String): ModelPropertyCore<Unit, ValueT>
}
typealias MapProperty<ModelT, PropertyT> = ModelMapProperty<ModelT, PropertyT>

fun <ModelT, PropertyT : Any> ModelSimpleProperty<ModelT, PropertyT>.bind(boundModel: ModelT): ModelSimpleProperty<Unit, PropertyT> = let {
  object : ModelSimpleProperty<Unit, PropertyT> {
    override fun getParsedValue(model: Unit): ParsedValue<PropertyT> = it.getParsedValue(boundModel)

    override fun setParsedValue(model: Unit, value: ParsedValue<PropertyT>) = it.setParsedValue(boundModel, value)

    override fun getResolvedValue(model: Unit): ResolvedValue<PropertyT> = it.getResolvedValue(boundModel)

    override val description: String = it.description

    override fun getDefaultValue(model: Unit): PropertyT? = it.getDefaultValue(boundModel)

    override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<PropertyT> = it.getValue(boundModel, property)

    override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<PropertyT>) = it.setValue(boundModel, property, value)

    override fun parse(value: String): ParsedValue<PropertyT> = it.parse(value)

    override fun getKnownValues(model: Unit): ListenableFuture<List<ValueDescriptor<PropertyT>>> = it.getKnownValues(boundModel)
  }
}
