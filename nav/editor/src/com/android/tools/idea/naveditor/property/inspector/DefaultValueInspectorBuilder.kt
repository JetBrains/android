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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isArgument
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.isNavigation
import com.android.tools.idea.naveditor.model.startDestination
import com.android.tools.idea.naveditor.property.ui.DefaultValueModel
import com.android.tools.idea.naveditor.property.ui.DefaultValuePanel
import com.android.tools.idea.naveditor.property.ui.DefaultValueTableModel
import com.android.tools.idea.uibuilder.property.NelePropertyItem
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable

class DefaultValueInspectorBuilder : InspectorBuilder<NelePropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val component = properties.first?.components?.singleOrNull() ?: return
    if (!component.isAction && !component.isNavigation || component.isInclude) {
      return
    }

    val arguments = getArguments(component)
    val list = arguments.map { DefaultValueModel(it, component) }

    val tableModel = DefaultValueTableModel(list)
    val panel = DefaultValuePanel(tableModel)

    val title = inspector.addExpandableTitle("Argument Default Values", list.isNotEmpty())
    val lineModel = inspector.addComponent(panel, title)
    lineModel.addValueChangedListener(ValueChangedListener { tableModel.fireTableDataChanged() })
  }

  private fun getArguments(component: NlComponent): List<NlComponent> {
    val destination = getDestination(component) ?: return listOf()

    return destination.children
      .filter { it.isArgument }
  }

  private fun getDestination(component: NlComponent): NlComponent? {
    if (component.isNavigation) {
      return component.startDestination
    }

    val destination = component.actionDestination ?: return null
    if (!destination.isNavigation) {
      return destination
    }

    return destination.startDestination
  }
}