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

/**
 * Which sort of binding logic should apply to some target layout XML.
 *
 * Note: This enum is serialized and de-serialized. Please only append.
 */
enum class BindingLayoutType {
  /**
   * The layout XML doesn't contain any data binding information, but a binding class will still be
   * generated only if view binding is enabled.
   */
  PLAIN_LAYOUT,

  /** The layout XML starts with `<layout>` tag, and possibly contains a `<data>` tag. */
  DATA_BINDING_LAYOUT,
}

/** Data class for storing information related to <variable> tags. */
data class VariableData(val name: String, val type: String)

/** Data class for storing information related to <import> tags. */
data class ImportData(val type: String, val alias: String?) {
  /**
   * An import's short name is its alias, if present, or its unqualified type. For example: `"Calc"`
   * for `<import alias='Calc' type='org.example.math.calc.Calculator'>` or `"Map"` for `<import
   * type='java.util.Map'>`)
   */
  val shortName
    get() = alias ?: type.substringAfterLast('.')
}

/**
 * Data class for storing information related to views with IDs.
 *
 * @param id id of the view.
 * @param viewName name of the view, typically the tag name: `<TextView>`.
 * @param layoutName optional layout attribute, only applicable to `<Merge>` or `<Include>` tags.
 * @param typeOverride optional tools attribute, gives a more specific type hint which can be useful
 *   for layouts with multiple configurations and different types per configuration.
 */
data class ViewIdData(
  val id: String,
  val viewName: String,
  val layoutName: String?,
  val typeOverride: String?,
)

/**
 * Data class for storing the indexed content of layouts we want to generate bindings for, e.g. data
 * binding or view binding candidates.
 *
 * For view binding data, many of these fields will be left empty.
 *
 * @param layoutType the type of binding for the target layout.
 * @param rootTag the name of the root xml tag, e.g. `layout`, `LinearLayout`, etc.
 * @param viewBindingIgnore set to true if binding should not be generated for this layout,
 *   currently only used for view binding layouts.
 * @param customBindingName optional value used to override the qualified class name of the
 *   generated binding.
 * @param imports data binding imports.
 * @param variables data binding variables.
 * @param viewIds ids of views defined in this layout.
 */
data class BindingXmlData(
  val layoutType: BindingLayoutType,
  val rootTag: String,
  val viewBindingIgnore: Boolean,
  val customBindingName: String?,
  val imports: Collection<ImportData>,
  val variables: Collection<VariableData>,
  val viewIds: Collection<ViewIdData>,
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

  /** Resolves an import's short name to its fully qualified class name, if found. */
  fun resolveImport(shortName: String): String? = findImport(shortName)?.type
}
