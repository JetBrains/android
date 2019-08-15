/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.index

import com.android.tools.idea.res.BindingLayoutType

/**
 * Data class for storing information related to <variable> tags.
 */
data class VariableData(
  val name: String,
  val type: String
)

/**
 * Data class for storing information related to <import> tags.
 */
data class ImportData(
  val type: String,
  val alias: String?
) {
  /**
   * An import's short name is its alias, if present, or its unqualified type.
   * For example:
   * `"Calc"` for `<import alias='Calc' type='org.example.math.calc.Calculator'>` or
   * `"Map"` for `<import type='java.util.Map'>`)
   */
  val shortName
    get() = alias ?: type.substringAfterLast('.')
}

/**
 * Data class for storing information related to views with IDs.
 */
data class ViewIdData(
  /** Id of the view. */
  val id: String,

  /** Name of the view. Typically the tag name: <TextView>. */
  val viewName: String,

  /** Optional layout attribute. Only applicable to <Merge> or <Include> tags. */
  val layoutName: String?
)

/**
 * Data class for storing the indexed content of layouts we want to generate bindings for,
 * e.g. data binding or view binding candidates.
 *
 * For view binding data, many of these fields will be left empty.
 */
data class BindingXmlData(
  /** Type of layout. */
  val layoutType: BindingLayoutType,

  /** Name used to affect the final Binding class path, if present. */
  val customBindingName: String?,

  /** Data binding import elements. */
  val imports: Collection<ImportData>,

  /** Data binding variable elements. */
  val variables: Collection<VariableData>,

  /** Ids of views defined in this layout. */
  val viewIds: Collection<ViewIdData>
) {
  private val importsMap = imports.associateBy { it.shortName }
  private val variablesMap = variables.associateBy { it.name }

  /**
   * Find an import using its short name.
   *
   * This search is backed by a map and should be preferred over iterating [imports] when possible.
   */
  fun findImport(shortName: String) = importsMap[shortName]

  /**
   * Find a variable using its name.
   *
   * This search is backed by a map and should be preferred over iterating [variables] when
   * possible.
   */
  fun findVariable(name: String) = variablesMap[name]
}
