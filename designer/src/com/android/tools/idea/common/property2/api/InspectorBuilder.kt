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
package com.android.tools.idea.common.property2.api

/**
 * Defines a builder of a section in a [InspectorPanel]. Each section is usually
 * visually and functionally separate from other sections and may contain editors for
 * multiple properties and even custom forms and collapsible subsections.
 */
interface InspectorBuilder<in P: PropertyItem> {
  /**
   * Add rows of controls to the inspector panel.
   *
   * @param inspector the inspector panel where the selected properties should be shown
   * @param properties the properties to generate UI controls for
   */
  fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<P>)

  /**
   * Reset any cached state.
   */
  fun resetCache() {}
}
