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

import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jetbrains.annotations.Nls
import java.io.File
import kotlin.properties.ReadWriteProperty

/**
 * Core methods of a UI property descriptor manipulating parsed values.
 */
interface ModelPropertyParsedCore<PropertyT : Any> {
  fun getParsedValue(): Annotated<ParsedValue<PropertyT>>
  fun setParsedValue(value: ParsedValue<PropertyT>)
  /**
   * Indicates whether the property is modified or is null if it cannot be determined.
   */
  val isModified: Boolean?
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
   * A property description as it should appear in the UI.
   */
  @get:Nls
  val description: String

  /**
   * A function returning a suitable default variable name for this property
   */
  fun getPreferredVariableName(): String = "var"

  /**
   * A function expressing whether we should offer extracting a variable for this property at all
   */
  fun getCanExtractVariable(): Boolean = true

  /**
   * The function to get the required scope of the property, or null if the default of the module or project scope is acceptable
   */
  val variableScope: (() -> PsVariablesScope?)?

  /**
   * The function to get the default value of the property for a given model, or null if the default value of the property cannot be
   * determined.
   */
  val defaultValueGetter: (() -> PropertyT?)?

  /**
   * If the parsed value does not match the resolved value returns an annotation describing the mismatch.
   *
   * Note: Returns [ValueAnnotation.Warning] if the resolved model is unavailable.
   *
   */
  fun annotateParsedResolvedMismatch(): ValueAnnotation?
}

fun <PropertyT : Any> ModelPropertyCore<PropertyT>.getValue(): Annotated<PropertyValue<PropertyT>> =
  PropertyValue(parsedValue = getParsedValue(), resolved = getResolvedValue()).annotateWith(annotateParsedResolvedMismatch())

/**
 * A UI descriptor of a property of a model of type [ModelT].
 */
interface ModelProperty<in ModelT, PropertyT : Any, ValueT : Any, out PropertyCoreT> :
  ReadWriteProperty<ModelT, ParsedValue<PropertyT>> {
  /**
   * A property description as it should appear in the UI.
   */
  @get:Nls
  val description: String

  /**
   * Returns a core property bound to [model].
   */
  fun bind(model: ModelT): PropertyCoreT

  /**
   * Returns a property context bound to [model].
   */
  fun bindContext(model: ModelT): ModelPropertyContext<ValueT>
}

/**
 * Metadata describing the well-known values and recognising variables suitable for a property.
 */
interface KnownValues<ValueT : Any> {
  val literals: List<ValueDescriptor<ValueT>>
  fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<ValueT>>): Boolean
}

fun <T : Any> emptyKnownValues() = object : KnownValues<T> {
  override val literals: List<ValueDescriptor<T>> = listOf()
  override fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<T>>): Boolean = false
}

@Suppress("AddVarianceModifier")  // PSQ erroneously reports AddVarianceModifier on ValueT here.
interface ModelPropertyContext<ValueT : Any> {
  /**
   * Parses the text representation of type [ValueT].
   */
  fun parse(value: String): Annotated<ParsedValue<ValueT>>

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

  /**
   * Parses the text representation of [ParsedValue<ValueT>].
   *
   * This is up to the parser to decide whether [value] is valid, invalid or is a DSL expression.
   */
  fun parseEditorText(text: String): Annotated<ParsedValue<ValueT>> = when {
    text.startsWith("\$\$") ->
      ParsedValue.Set.Parsed(value = null, dslText = DslText.OtherUnparsedDslText(text.substring(2))).annotated()
    text.startsWith("\$") ->
      ParsedValue.Set.Parsed<ValueT>(value = null, dslText = DslText.Reference(text.substring(1))).annotated()
    text.startsWith("\"") && text.endsWith("\"") ->
      ParsedValue.Set.Parsed<ValueT>(value = null, dslText = DslText.InterpolatedString(text.substring(1, text.length - 1))).annotated()
    else -> parse(text)
  }
}

/**
 * An extension to a property context providing the details to the file chooser UI.
 */
interface FileTypePropertyContext<ValueT: Any>  : ModelPropertyContext<ValueT> {
  val browseRootDir: File?
  val resolveRootDir: File?
  val filterPredicate: ((File) -> Boolean)?
}

/**
 * A UI descriptor of a simple-typed property.
 *
 * The simple-types property is a property whose value can be easily represented in the UI as text.
 */
interface ModelSimpleProperty<in ModelT, PropertyT : Any> :
  ModelProperty<ModelT, PropertyT, PropertyT, ModelPropertyCore<PropertyT>>
typealias SimpleProperty<ModelT, PropertyT> = ModelSimpleProperty<ModelT, PropertyT>

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
interface ModelListProperty<in ModelT, PropertyT : Any> :
  ModelProperty<ModelT, List<PropertyT>, PropertyT, ModelListPropertyCore<PropertyT>>
typealias ListProperty<ModelT, PropertyT> = ModelListProperty<ModelT, PropertyT>

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
interface ModelMapProperty<in ModelT, PropertyT : Any> :
  ModelProperty<ModelT, Map<String, PropertyT>, PropertyT, ModelMapPropertyCore<PropertyT>>
typealias MapProperty<ModelT, PropertyT> = ModelMapProperty<ModelT, PropertyT>

/**
 * Creates a value formatter function that can be passed to [ParsedValue] renderers.
 */
fun <ValueT : Any> ModelPropertyContext<ValueT>.valueFormatter(): ValueT.() -> String =
  { this@valueFormatter.format(this) }

/**
 * The standard implementation of [ModelPropertyCore.annotateParsedResolvedMismatch] that requires a matcher function [matcher].
 */
inline fun <T : Any> ModelPropertyCore<T>.annotateParsedResolvedMismatchBy(
  matcher: (parsedValue: T?, resolvedValue: T) -> Boolean
): ValueAnnotation? {
  if (isModified == false) {
    val resolvedValue = (getResolvedValue() as? ResolvedValue.Set<T>)?.resolved
    if (resolvedValue != null) {
      val parsedValue = getParsedValue().value
      val parsedValueToCompare = when (parsedValue) {
        is ParsedValue.NotSet -> (defaultValueGetter ?: return null)()
        is ParsedValue.Set.Parsed -> parsedValue.value
      }
      return if (!matcher(parsedValueToCompare, resolvedValue)) {
        ValueAnnotation.Error("Resolved: $resolvedValue")
      }
      else {
        null  // In the case of a match return no annotations.
      }
    }
  }
  return ValueAnnotation.Warning("Resolved value is unavailable.")
}

class SimplePropertyStub<ValueT : Any> : ModelPropertyCore<ValueT> {
  override val description: String = ""
  override fun getPreferredVariableName(): String = "var"
  override fun getCanExtractVariable(): Boolean = true
  override fun getParsedValue(): Annotated<ParsedValue<ValueT>> = ParsedValue.NotSet.annotated()
  override fun setParsedValue(value: ParsedValue<ValueT>) = Unit
  override fun getResolvedValue(): ResolvedValue<ValueT> = ResolvedValue.NotResolved()
  override val defaultValueGetter: (() -> ValueT?)? = null
  override val variableScope: (() -> PsVariablesScope?)? = null
  override val isModified: Boolean? = null
  override fun annotateParsedResolvedMismatch(): ValueAnnotation? = null
}

open class PropertyContextStub<ValueT : Any> : ModelPropertyContext<ValueT> {
  override fun parse(value: String): Annotated<ParsedValue<ValueT>> = throw UnsupportedOperationException()
  override fun format(value: ValueT): String = value.toString()
  override fun getKnownValues(): ListenableFuture<KnownValues<ValueT>> = Futures.immediateCancelledFuture()
}
