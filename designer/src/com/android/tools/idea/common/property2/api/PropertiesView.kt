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
 * A view is defined by a [PropertiesModel] and a list of [InspectorBuilder]s.
 * Each view controls an aspect of the properties inspector.
 *
 * For example: the property panel in Nele may show properties of
 * an NlComponent when a such a component s selected, and show the
 * properties of a key frame in the motion editor when a key frame
 * is selected.
 */
class PropertiesView<P: PropertyItem>(val model: PropertiesModel<P>) {
  val builders = mutableListOf<InspectorBuilder<P>>()

  fun attachToInspector(inspector: InspectorPanel) {
    builders.forEach { it.attachToInspector(inspector, model.properties) }
  }
}
