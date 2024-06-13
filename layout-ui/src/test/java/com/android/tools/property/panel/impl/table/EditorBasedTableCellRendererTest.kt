/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.property.panel.impl.table

import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.impl.model.util.FakeFlagsPropertyItem
import com.android.tools.property.panel.impl.model.util.FakeLinkPropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.DefaultPTableCellRenderer
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellRenderer
import com.android.tools.property.ptable.PTableCellRendererProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableItem
import com.android.tools.property.ptable.PTableModel
import com.android.tools.property.ptable.impl.PTableImpl
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.TableCell
import com.intellij.ui.TableExpandableItemsHandler
import com.intellij.ui.table.JBTable
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontSize
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable

private const val LONG_STRING_VALUE = "A very long long long string value"
private const val ROW_HEIGHT = 22

class EditorBasedTableCellRendererTest {

  @get:Rule
  val rules = RuleChain.outerRule(ApplicationRule()).around(IconLoaderRule()).around(EdtRule())!!

  @RunsInEdt
  @Test
  fun testExpansionHotZoneOfRenderers() {
    // Test that expansion on hove will happen in the right places in several of the control types.
    val items = createItemsByControlType()
    val renderer = createRenderer(items, performLayout = true)
    val table = createTable(items, renderer)
    table.component.setSize(200, 5000)
    val ui = FakeUi(table.component, createFakeWindow = true)

    // A Text Editor should expand when hovering over the text:
    assertThat(isExpansionHotZone(ui, items, ControlType.TEXT_EDITOR, 50)).isTrue()
    // A Text Editor should not expand when hovering over the browse button:
    assertThat(isExpansionHotZone(ui, items, ControlType.TEXT_EDITOR, 92)).isFalse()

    // A ComboBox should expand when hovering on the text:
    assertThat(isExpansionHotZone(ui, items, ControlType.COMBO_BOX, 50)).isTrue()
    // A ComboBox should not expand when hovering over the browse button:
    assertThat(isExpansionHotZone(ui, items, ControlType.COMBO_BOX, 92)).isFalse()
    // A ComboBox should not expand when hovering over the dropdown button:
    assertThat(isExpansionHotZone(ui, items, ControlType.COMBO_BOX, 75)).isFalse()

    // A Color control should expand when hovering on the text:
    assertThat(isExpansionHotZone(ui, items, ControlType.COLOR_EDITOR, 50)).isTrue()
    // A Color control should not expand when hovering over the browse button:
    assertThat(isExpansionHotZone(ui, items, ControlType.COLOR_EDITOR, 92)).isFalse()
    // A Color control should not expand when hovering over the color selector/box:
    assertThat(isExpansionHotZone(ui, items, ControlType.COLOR_EDITOR, 10)).isFalse()

    // A Boolean control should expand when hovering on the text:
    assertThat(isExpansionHotZone(ui, items, ControlType.BOOLEAN, 50)).isTrue()
    assertThat(isExpansionHotZone(ui, items, ControlType.THREE_STATE_BOOLEAN, 50)).isTrue()
    // A Boolean control should not expand when hovering over the browse button:
    assertThat(isExpansionHotZone(ui, items, ControlType.BOOLEAN, 92)).isFalse()
    assertThat(isExpansionHotZone(ui, items, ControlType.THREE_STATE_BOOLEAN, 92)).isFalse()
    // A Boolean control should not expand when hovering over the color selector/box:
    assertThat(isExpansionHotZone(ui, items, ControlType.BOOLEAN, 10)).isFalse()
    assertThat(isExpansionHotZone(ui, items, ControlType.THREE_STATE_BOOLEAN, 10)).isFalse()
  }

  /** Return true if the cell renderer for the specified [controlType] is an expansion hot zone. */
  private fun isExpansionHotZone(
    ui: FakeUi,
    items: BiMap<ControlType, FakePropertyItem>,
    controlType: ControlType,
    x: Int,
  ): Boolean {
    val table = ui.root as PTableImpl
    val item = items[controlType]!!
    val row = table.tableModel.items.indexOf(item)
    val rect = table.getCellRect(row, 1, true)
    // First move out of the cell to reset any pending expansions
    ui.mouse.moveTo(rect.x + x, rect.y + rect.height + 10)
    // Then move to the actual cell
    ui.mouse.moveTo(rect.x + x, rect.centerY.toInt())
    UIUtil.dispatchAllInvocationEvents()
    findExpansionAlarm(table).drainRequestsInTest()
    return table.isExpandedItem(row, 1)
  }

  private fun findExpansionAlarm(table: PTableImpl): Alarm {
    val field =
      AbstractExpandableItemsHandler::class.java.getDeclaredField("myUpdateAlarm").apply {
        isAccessible = true
      }
    return field.get(table.expandableItemsHandler) as Alarm
  }

  private fun paint(component: JComponent): BufferedImage {
    @Suppress("UndesirableClassUsage")
    val generatedImage =
      BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    component.paint(graphics)
    return generatedImage
  }

  private fun createComponent(
    table: PTable,
    renderer: PTableCellRenderer,
    item: PTableItem,
    emulateExpansionState: TableExpansionState,
  ): JComponent {
    table.expansionHandler.emulate(emulateExpansionState, item)
    val component =
      renderer.getEditorComponent(
        table,
        item,
        PTableColumn.VALUE,
        0,
        isSelected = false,
        hasFocus = false,
        isExpanded = false,
      ) as JComponent

    val wrapped =
      if (emulateExpansionState == TableExpansionState.EXPANDED_POPUP) {
        val cell = table.expansionHandler.expandedItems.single()
        val rendererAndBounds = table.expansionHandler.computeCellRendererAndBounds(cell)
        component.apply { size = Dimension(rendererAndBounds!!.second.width, ROW_HEIGHT) }
      } else {
        JPanel(BorderLayout()).apply {
          border = JBUI.Borders.empty()
          setSize(100, ROW_HEIGHT)
          add(component, BorderLayout.CENTER)
        }
      }
    val drawTable = JBTable()
    drawTable.add(wrapped)
    FakeUi(drawTable, createFakeWindow = true)
    return wrapped
  }

  private val PTable.expansionHandler: FakeExpandableItemsHandler
    get() = (component as JBTable).expandableItemsHandler as FakeExpandableItemsHandler

  private fun createTable(
    items: BiMap<ControlType, FakePropertyItem>,
    renderer: PTableCellRenderer,
    withFakeHandler: Boolean = false,
  ): PTable {
    val itemList = items.values.toList()
    val model =
      object : PTableModel {
        override val items = itemList
        override var editedItem: PTableItem? = null

        override fun addItem(item: PTableItem): PTableItem = error("Not supported")

        override fun removeItem(item: PTableItem) = error("Not supported")
      }
    val provider =
      object : PTableCellRendererProvider {
        override fun invoke(
          table: PTable,
          item: PTableItem,
          colum: PTableColumn,
        ): PTableCellRenderer = renderer
      }
    return object :
      PTableImpl(model, rendererProvider = provider, nameColumnFraction = ColumnFraction(0.5f)) {
      init {
        setRowHeight(ROW_HEIGHT)
      }

      override fun createExpandableItemsHandler(): ExpandableItemsHandler<TableCell> {
        return if (withFakeHandler) FakeExpandableItemsHandler(this)
        else super.createExpandableItemsHandler()
      }
    }
  }

  private fun createRenderer(
    items: BiMap<ControlType, FakePropertyItem>,
    performLayout: Boolean = false,
  ): PTableCellRenderer {
    val controlTypeProvider =
      object : ControlTypeProvider<FakePropertyItem> {
        override fun invoke(item: FakePropertyItem): ControlType = items.inverse()[item]!!
      }
    val enumSupportProvider =
      object : EnumSupportProvider<FakePropertyItem> {
        override fun invoke(item: FakePropertyItem): EnumSupport? =
          when (item.name) {
            "combo_box",
            "dropdown" -> EnumSupport.simple("one", "two")
            else -> null
          }
      }
    val renderer =
      EditorBasedTableCellRenderer(
        FakePropertyItem::class.java,
        controlTypeProvider,
        EditorProvider.create(enumSupportProvider, controlTypeProvider),
        FontSize.NORMAL,
        DefaultPTableCellRenderer(),
      )
    val wrapped =
      object : PTableCellRenderer {
        override fun getEditorComponent(
          table: PTable,
          item: PTableItem,
          column: PTableColumn,
          depth: Int,
          isSelected: Boolean,
          hasFocus: Boolean,
          isExpanded: Boolean,
        ): JComponent? {
          val component =
            renderer.getEditorComponent(
              table,
              item,
              column,
              depth,
              isSelected,
              hasFocus,
              isExpanded,
            )
          if (performLayout) {
            val row = table.tableModel.items.indexOf(item)
            val rect = (table.component as JTable).getCellRect(row, column.ordinal, true)
            component?.bounds = rect
            component?.performLayout()
          }
          return component
        }

        private fun Component.performLayout() {
          doLayout()
          (this as? Container)?.components?.forEach { it.performLayout() }
        }
      }
    return wrapped
  }

  private fun createItemsByControlType(): BiMap<ControlType, FakePropertyItem> {
    val support =
      object : EditingSupport {
        override val validation: EditingValidation
          get() = { Pair(EditingErrorCategory.ERROR, "") }
      }
    val color =
      object : ActionIconButton {
        override val actionButtonFocusable = true
        override val actionIcon = StudioIcons.LayoutEditor.Properties.IMAGE_PICKER
        override val action = null
      }
    val browse =
      object : ActionIconButton {
        override val actionButtonFocusable = true
        override val actionIcon = StudioIcons.Common.PROPERTY_UNBOUND
        override val action = null
      }
    val link =
      object : AnAction("Text.kt:245") {
        override fun actionPerformed(e: AnActionEvent) {}
      }
    val map = HashBiMap.create<ControlType, FakePropertyItem>()
    map[ControlType.TEXT_EDITOR] =
      FakePropertyItem("", "text", LONG_STRING_VALUE, browse, null, support)
    map[ControlType.COLOR_EDITOR] =
      FakePropertyItem("", "color", LONG_STRING_VALUE, browse, color, support)
    map[ControlType.COMBO_BOX] =
      FakePropertyItem("", "combo_box", LONG_STRING_VALUE, browse, color, support)
    map[ControlType.DROPDOWN] =
      FakePropertyItem("", "dropdown", LONG_STRING_VALUE, browse, color, support)
    map[ControlType.BOOLEAN] =
      FakePropertyItem("", "boolean", LONG_STRING_VALUE, browse, color, support)
    map[ControlType.THREE_STATE_BOOLEAN] =
      FakePropertyItem("", "3_boolean", LONG_STRING_VALUE, browse, color, support)
    map[ControlType.FLAG_EDITOR] =
      FakeFlagsPropertyItem(
        "",
        "flags",
        listOf("one", "two", "three", "four"),
        listOf(1, 2, 4, 8),
        "one, two, three",
      )
    map[ControlType.LINK_EDITOR] = FakeLinkPropertyItem("", "link", "bla bla", link)
    return map
  }

  private class FakeExpandableItemsHandler(private val table: PTable) :
    TableExpandableItemsHandler(table.component as JTable) {
    private var expandedCell: TableCell? = null
    private var isPopupShowing = false

    fun emulate(state: TableExpansionState, item: PTableItem) {
      val row = table.tableModel.items.indexOf(item)
      expandedCell = if (state != TableExpansionState.NORMAL) TableCell(row, 1) else null
      ClientProperty.put(
        table.component,
        ExpandableItemsHandler.EXPANDED_RENDERER,
        state == TableExpansionState.EXPANDED_POPUP,
      )
      isPopupShowing = state == TableExpansionState.EXPANDED_CELL_FOR_POPUP
    }

    override fun getExpandedItems(): Collection<TableCell> {
      return expandedCell?.let { listOf(it) } ?: emptyList()
    }

    override fun isShowing(): Boolean = isPopupShowing

    fun computeCellRendererAndBounds(
      key: TableCell
    ): com.intellij.openapi.util.Pair<Component, Rectangle>? {
      return getCellRendererAndBounds(key)
    }
  }
}
