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

import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellRenderer
import com.android.tools.property.ptable2.PTableCellRendererProvider
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.PTableModel
import com.intellij.ui.SimpleColoredRenderer
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import javax.swing.JComponent

private const val X = "x"
private const val Y = "y"
private const val HEIGHT = "height"
private const val WIDTH = "width"

/**
 * Adds the bounds of a view to the layout inspectors property table.
 *
 * Currently displayed are: x, y, width, height where the position is relative to the top
 * left of the device.
 */
class DimensionBuilder : InspectorBuilder<InspectorPropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<InspectorPropertyItem>) {
    val view = properties.first?.view ?: return
    val table = PTable.create(DimensionTableModel(view), null, RendererProvider())
    inspector.addComponent(table.component)
  }

  private class DimensionTableModel(view: ViewNode): PTableModel {
    override val items = createDimensionItems(view)
    override var editedItem: PTableItem? = null

    private fun createDimensionItems(view: ViewNode): List<PTableItem> {
      return listOf(
        Item(X, view.bounds.x.toString()),
        Item(Y, view.bounds.y.toString()),
        Item(WIDTH, view.bounds.width.toString()),
        Item(HEIGHT, view.bounds.height.toString()))
    }
  }

  private class Item(override val name: String, override val value: String) : PTableItem

  private class RendererProvider : PTableCellRendererProvider {
    private val renderer = Renderer()

    override fun invoke(table: PTable, item: PTableItem, column: PTableColumn): PTableCellRenderer {
      return renderer
    }
  }

  private class Renderer: PTableCellRenderer {
    private val textRenderer = SimpleColoredRenderer()
    private var emptyIconCache = EmptyIcon.create(UIUtil.getTreeCollapsedIcon())

    override fun getEditorComponent(table: PTable,
                                    item: PTableItem,
                                    column: PTableColumn,
                                    depth: Int,
                                    isSelected: Boolean,
                                    hasFocus: Boolean,
                                    isExpanded: Boolean): JComponent? {
      textRenderer.clear()
      if (column == PTableColumn.NAME) {
        textRenderer.border = JBUI.Borders.empty()
        textRenderer.ipad = JBUI.insets(0, 3)
        textRenderer.icon = emptyIcon
        textRenderer.font = UIUtil.getFont(UIUtil.FontSize.SMALL, UIUtil.getLabelFont())
        textRenderer.append(item.name)
      }
      else {
        textRenderer.border = JBUI.Borders.customLine(table.gridLineColor, 0, 1, 0, 0)
        textRenderer.ipad = JBUI.insets(0, 3)
        textRenderer.font = UIUtil.getLabelFont()
        textRenderer.append(item.value ?: "")
      }
      return textRenderer
    }

    private val emptyIcon: Icon
      get() {
        if (emptyIconCache.iconWidth != UIUtil.getTreeCollapsedIcon().iconWidth) {
          emptyIconCache = EmptyIcon.create(UIUtil.getTreeCollapsedIcon())
        }
        return emptyIconCache
      }
  }
}
