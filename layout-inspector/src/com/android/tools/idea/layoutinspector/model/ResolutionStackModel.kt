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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Model for keeping track of expanded detail traces in an attribute resolution stack.
 *
 * The detail traces are shown if there are references found in attribute values. A
 * [ResolutionStackModel] is required per table (i.e. 1 for declared attributes and 1 for all
 * attributes).
 */
class ResolutionStackModel(val propertiesModel: InspectorPropertiesModel) : Disposable {
  private val expandedItems = mutableSetOf<InspectorPropertyItem>()
  private val listener =
    object : PropertiesModelListener<InspectorPropertyItem> {
      var updates = 0

      override fun propertiesGenerated(model: PropertiesModel<InspectorPropertyItem>) {
        if (updates != propertiesModel.structuralUpdates) {
          // Reset the expanded items when a significant structural change happens:
          clear()
          updates = propertiesModel.structuralUpdates
        }
      }
    }

  init {
    Disposer.register(propertiesModel, this)

    propertiesModel.addListener(listener)
  }

  override fun dispose() {
    propertiesModel.removeListener(listener)
  }

  fun isExpanded(property: InspectorPropertyItem): Boolean = expandedItems.contains(property)

  fun toggle(property: InspectorPropertyItem) {
    if (expandedItems.contains(property)) {
      expandedItems.remove(property)
    } else {
      expandedItems.add(property)
    }
  }

  private fun clear() {
    expandedItems.clear()
  }
}
