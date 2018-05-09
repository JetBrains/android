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

/**
 * Core methods of a UI property descriptor manipulating parsed values.
 */
interface ModelPropertyParsedCore<PropertyT : Any> {
  fun getParsedValue(): ParsedValue<PropertyT>
  fun setParsedValue(value: ParsedValue<PropertyT>)
}

/**
 * Core methods of a UI property descriptor manipulating resolved values.
 */
interface ModelPropertyResolvedCore<out PropertyT : Any> {
  fun getResolvedValue(): ResolvedValue<PropertyT>
}

/**
 * A UI core descriptor of a property.
 */
interface ModelPropertyCore<PropertyT : Any> :
  ModelPropertyParsedCore<PropertyT>,
  ModelPropertyResolvedCore<PropertyT> {
  /**
   * The function to get the default value of the property for a given model, or null if the default value of the property cannot be
   * determined.
   */
  val defaultValueGetter: (() -> PropertyT?)?
}

fun <PropertyT : Any> ModelPropertyCore<PropertyT>.getValue(): PropertyValue<PropertyT> =
  PropertyValue(parsedValue = getParsedValue(), resolved = getResolvedValue())

/**
 * A UI descriptor of a property of a model of type [ModelT].
 */
interface ModelProperty<in ContextT, in ModelT, PropertyT : Any, ValueT : Any, out PropertyCoreT> :
  ReadWriteProperty<ModelT, ParsedValue<PropertyT>> {
  /**
   * A property description as it should appear in the UI.
   */
  val description: String

  /**
   * Returns a core property bound to [model].
   */
  fun bind(model: ModelT): PropertyCoreT

  /**
   * Returns a property context bound to [context] and [model].
   */
  fun bindContext(context: ContextT, model: ModelT): ModelPropertyContext<ValueT>
}

/**
 * Metadata describing the well-known values and recognising variables suitable for a property.
 */
interface KnownValues<ValueT> {
  val literals: List<ValueDescriptor<ValueT>>
  fun isSuitableVariable(variable: ParsedValue.Set.Parsed<ValueT>): Boolean
}

fun <T> emptyKnownValues() = object : KnownValues<T> {
  override val literals: List<ValueDescriptor<T>> = listOf()
  override fun isSuitableVariable(variable: ParsedValue.Set.Parsed<T>): Boolean = false
}

@Suppress("AddVarianceModifier")  // PSQ erroneously reports AddVarianceModifier on ValueT here.
interface ModelPropertyContext<ValueT : Any> {
  /**
   * Parses the text representation of type [ValueT].
   *
   * This is up to the parser to decide whether [value] is valid, invalid or is a DSL expression.
   */
  fun parse(value: String): ParsedValue<ValueT>

  /**
   * Formats the text representation of [value].
   *
   * This is up to the parser to decide whether [value] is valid, invalid or is a DSL expression.
   */
  fun format(value: ValueT): String

  /**
   * Returns a list of well-known values (constants) with their short human-readable descriptions that are applicable to the property.
   */
  fun getKnownValues(): ListenableFuture<KnownValues<ValueT>>
}

/**
 * A UI descriptor of a simple-typed property.
 *
 * The simple-types property is a property whose value can be easily represented in the UI as text.
 */
interface ModelSimpleProperty<in ContextT, in ModelT, PropertyT : Any> :
  ModelProperty<ContextT, ModelT, PropertyT, PropertyT, ModelPropertyCore<PropertyT>> 
typealias SimpleProperty<ModelT, PropertyT> = ModelSimpleProperty<Nothing?, ModelT, PropertyT>

/**
 * A UI descriptor of a collection property core.
 */
interface ModelCollectionPropertyCore<CollectionT : Any> : ModelPropertyCore<CollectionT>

/**
 * A UI descriptor of a list property core.
 */
interface ModelListPropertyCore<ValueT : Any> :
  ModelCollectionPropertyCore<List<ValueT>> {
  fun getEditableValues(): List<ModelPropertyCore<ValueT>>
  fun addItem(index: Int): ModelPropertyCore<ValueT>
  fun deleteItem(index: Int)
}

/**
 * A UI descriptor of a list property.
 */
interface ModelListProperty<in ContextT, in ModelT, PropertyT : Any> : ModelProperty<ContextT, ModelT, List<PropertyT>, PropertyT, ModelListPropertyCore<PropertyT>>
typealias ListProperty<ModelT, PropertyT> = ModelListProperty<Nothing?, ModelT, PropertyT>

/**
 * A UI descriptor of a map property core.
 */
interface ModelMapPropertyCore<ValueT : Any> : ModelCollectionPropertyCore<Map<String, ValueT>> {
  fun getEditableValues(): Map<String, ModelPropertyCore<ValueT>>
  fun addEntry(key: String): ModelPropertyCore<ValueT>
  fun deleteEntry(key: String)
  fun changeEntryKey(old: String, new: String): ModelPropertyCore<ValueT>
}

/**
 * A UI descriptor of a map property.
 */
interface ModelMapProperty<in ContextT, in ModelT, PropertyT : Any> : ModelProperty<ContextT, ModelT, Map<String, PropertyT>, PropertyT, ModelMapPropertyCore<PropertyT>>
typealias MapProperty<ModelT, PropertyT> = ModelMapProperty<Nothing?, ModelT, PropertyT>

/**
 * Creates a value formatter function that can be passed to [ParsedValue] renderers.
 */
fun <ValueT : Any> ModelPropertyContext<ValueT>.valueFormatter(): ValueT.() -> String =
  { this@valueFormatter.format(this) }

