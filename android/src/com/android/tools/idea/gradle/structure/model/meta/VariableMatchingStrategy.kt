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
package com.android.tools.idea.gradle.structure.model.meta

enum class VariableMatchingStrategy {
  /**
   * The strategy matching no variables to the property.
   */
  NONE {
    override fun <T> matches(variable: ParsedValue.Set.Parsed<T>, knownValues: Set<T>): Boolean = false
  },
  /**
   * The strategy matching to the property the variables whose current values are in the set of the well-known values.
   */
  WELL_KNOWN_VALUE {
    override fun <T> matches(variable: ParsedValue.Set.Parsed<T>, knownValues: Set<T>): Boolean = knownValues.contains(variable.value)
    override fun <T : Any> prepare(descriptors: List<ValueDescriptor<T>>): Set<T> =
      descriptors.mapNotNull { it.value.maybeValue }.toSet()
  },
  /**
   * The strategy matching to the property the variables compatible by the type of their current values.
   */
  BY_TYPE {
    override fun <T> matches(variable: ParsedValue.Set.Parsed<T>, knownValues: Set<T>): Boolean = true
  };

  internal abstract fun <T> matches(variable: ParsedValue.Set.Parsed<T>, knownValues: Set<T>): Boolean
  internal open fun <T : Any> prepare(descriptors: List<ValueDescriptor<T>>): Set<T> = setOf()
}