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
package com.android.tools.idea.common.property2.api

/**
 * Defines a view in a [PropertiesPanel].
 *
 * A view defines a separate set of viewable properties.
 *
 * For example:
 *  - One view for displaying component properties in Nele
 *  - Another view for displaying key frame properties in Motion Editor
 *
 * The [id] of this view, (used for storing preferences).
 * Use [addTab] to create a named tab [PropertiesViewTab].
 * Each tab will be shown on a separate tab in the properties panel.
 */
class PropertiesView<P: PropertyItem>(val id: String, val model: PropertiesModel<P>) {
  val tabs = mutableListOf<PropertiesViewTab<P>>()

  fun addTab(name: String): PropertiesViewTab<P> {
    val tab = PropertiesViewTab(name, model)
    tabs.add(tab)
    return tab
  }
}

/**
 * Defines a tab in a [PropertiesPanel].
 *
 * A tab definition consist of:
 * - a list [builders] of [InspectorBuilder] which defines the UI on the tab.
 * - [searchable] which controls if a tab should be visible during search (default is true).
 */
class PropertiesViewTab<P: PropertyItem>(val name: String, private val model: PropertiesModel<P>) {
  val builders = mutableListOf<InspectorBuilder<P>>()
  var searchable = true

  fun attachToInspector(inspector: InspectorPanel) {
    builders.forEach { it.attachToInspector(inspector, model.properties) }
  }
}
