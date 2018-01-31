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

import kotlin.properties.ReadWriteProperty

/**
 * A UI core descriptor of a model of type [ModelT].
 */
interface ModelPropertyCore<in ModelT, PropertyT : Any> {
  fun getValue(model: ModelT): PropertyValue<PropertyT>
  fun setValue(model: ModelT, value: ParsedValue<PropertyT>)
}

/**
 * A UI descriptor a property of a model of type [ModelT].
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

/**
 * A UI descriptor of a simple-typed property.
 *
 * The simple-types property is a property whose value can be easily represented in the UI as text.
 */
interface ModelSimpleProperty<in ModelT, PropertyT : Any> : ModelProperty<ModelT, PropertyT> {
  /**
   * Parses the text representation of type [PropertyT].
   *
   * This is up to the parser to decide whether [value] is valid, invalid or is a DSL expression.
   */
  fun parse(value: String): ParsedValue<PropertyT>

  /**
   * Returns a list of well-known values (constants) with their short human-readable descriptions that are applicable to the property.
   */
  fun getKnownValues(model: ModelT): List<ValueDescriptor<PropertyT>>?
}
