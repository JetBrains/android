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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_WIDTH
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel

/**
 * Adds the bounds of a view to the layout inspectors property table.
 *
 * Currently displayed are: x, y, width, height where the position is relative to the top left of
 * the device.
 */
object DimensionBuilder : InspectorBuilder<InspectorPropertyItem> {

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<InspectorPropertyItem>,
  ) {
    val tableModel = DimensionTableModel(properties)
    val enumSupportProvider =
      object : EnumSupportProvider<InspectorPropertyItem> {
        // TODO: Make this a 1 liner
        override fun invoke(property: InspectorPropertyItem): EnumSupport? = null
      }
    val controlTypeProvider =
      object : ControlTypeProvider<InspectorPropertyItem> {
        // TODO: Make this a 1 liner
        override fun invoke(property: InspectorPropertyItem): ControlType = ControlType.TEXT_EDITOR
      }
    val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
    val uiProvider = TableUIProvider(controlTypeProvider, editorProvider)
    inspector.addTable(tableModel, true, uiProvider)
  }

  private class DimensionTableModel(properties: PropertiesTable<InspectorPropertyItem>) :
    PTableModel {
    override val items = createDimensionItems(properties)
    override var editedItem: PTableItem? = null

    private fun createDimensionItems(
      properties: PropertiesTable<InspectorPropertyItem>
    ): List<PTableItem> {
      return listOfNotNull(
        properties.getOrNull(NAMESPACE_INTERNAL, ATTR_X),
        properties.getOrNull(NAMESPACE_INTERNAL, ATTR_Y),
        properties.getOrNull(NAMESPACE_INTERNAL, ATTR_WIDTH),
        properties.getOrNull(NAMESPACE_INTERNAL, ATTR_HEIGHT),
      )
    }

    override fun addItem(item: PTableItem): PTableItem {
      // Not supported
      return item
    }

    override fun removeItem(item: PTableItem) {
      // Not supported
    }
  }
}
