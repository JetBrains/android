/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.util.preferredWidth
import org.junit.Rule
import org.junit.Test
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.SortOrder
import javax.swing.SwingUtilities.convertPoint

@RunsInEdt
class CategoryTableTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  fun createScrollPane(table: CategoryTable<*>) =
    JBScrollPane().also {
      it.setBounds(0, 0, 800, 400)
      // This clears the default 1px insets of the ScrollPane to keep layout simpler
      it.border = BorderFactory.createEmptyBorder()
      table.addToScrollPane(it)
    }

  @Test
  fun group() {
    val values = CategoryTableDemo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute), emptyList())
    assertThat(sorted.map { it.status })
      .containsExactly("Offline", "Offline", "Offline", "Online", "Online", "Online")
  }

  @Test
  fun nestedGroup() {
    val values = CategoryTableDemo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute, Type.attribute), emptyList())
    assertThat(sorted.map { it.type })
      .containsExactly("Phone", "Phone", "Tablet", "Phone", "Phone", "Tablet")
  }

  @Test
  fun addOrUpdateRow() {
    val table = CategoryTable(CategoryTableDemo.columns)
    table.addGrouping(Type)
    val device = CategoryTableDemo.devices[0]

    assertThat(table.addOrUpdateRow(device)).isTrue()
    assertThat(table.addOrUpdateRow(device)).isFalse()

    assertThat(table.rowComponents).hasSize(2)
    assertThat(table.rowComponents[0]).isInstanceOf(CategoryRowComponent::class.java)
    assertThat(table.rowComponents[1]).isInstanceOf(ValueRowComponent::class.java)
  }

  @Test
  fun addOrUpdateRow_withPrimaryKey() {
    val table = CategoryTable(CategoryTableDemo.columns, { it.name })
    table.addGrouping(Type)
    val offlineNexus7 = CategoryTableDemo.devices[4]
    val onlineNexus7 = CategoryTableDemo.devices[5]

    assertThat(table.addOrUpdateRow(offlineNexus7)).isTrue()
    assertThat(table.addOrUpdateRow(onlineNexus7)).isFalse()
    assertThat(table.rowComponents).hasSize(2)
  }

  @Test
  fun addOrUpdateRow_withPosition() {
    val table = CategoryTable(CategoryTableDemo.columns, { it.name })
    val device = CategoryTableDemo.Device("Pixel 4", "33", "Phone", "Offline")
    table.toggleSortOrder(Api.attribute)
    listOf(
        device,
        device.copy(name = "Pixel 5"),
        device.copy(name = "Pixel 6"),
        device.copy(name = "Pixel 7")
      )
      .forEach { table.addOrUpdateRow(it) }

    assertThat(table.values.map { it.name })
      .containsExactly("Pixel 4", "Pixel 5", "Pixel 6", "Pixel 7")
      .inOrder()

    table.addOrUpdateRow(device.copy(name = "Pixel 4a"), "Pixel 5")
    table.addOrUpdateRow(device.copy(name = "Pixel 3a", api = "32"), "Pixel 5")

    assertThat(table.values.map { it.name })
      .containsExactly("Pixel 3a", "Pixel 4", "Pixel 4a", "Pixel 5", "Pixel 6", "Pixel 7")
      .inOrder()
  }

  @Test
  fun removeRow() {
    val table = CategoryTable(CategoryTableDemo.columns)
    table.addGrouping(Type)
    val device = CategoryTableDemo.devices[0]

    assertThat(table.addOrUpdateRow(device)).isTrue()
    table.removeRow(device)
    assertThat(table.rowComponents).hasSize(0)
  }

  @Test
  fun removeRowByKey() {
    val table = CategoryTable(CategoryTableDemo.columns, { it.name })
    table.addGrouping(Type)
    val offlineNexus7 = CategoryTableDemo.devices[4]
    val onlineNexus7 = CategoryTableDemo.devices[5]

    assertThat(table.addOrUpdateRow(offlineNexus7)).isTrue()
    table.removeRowByKey(onlineNexus7.name)
    assertThat(table.rowComponents).hasSize(0)
  }

  @Test
  fun addAndRemoveGrouping() {
    val table = CategoryTable(CategoryTableDemo.columns)

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }
    assertThat(table.rowComponents).hasSize(6)

    table.toggleSortOrder(Name.attribute)
    table.addGrouping(Api)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "25",
        "Nexus 7",
        "26",
        "Nexus 7",
        "31",
        "Pixel 6",
        "Pixel 6a",
        "32",
        "Pixel 5",
        "33",
        "Pixel 7"
      )
    assertThat(table.header.columnModel.columnList.map { it.headerValue })
      .containsExactly("Name", "Status", "Type", "Actions")

    table.addGrouping(Type)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "25",
        "25, Tablet",
        "Nexus 7",
        "26",
        "26, Tablet",
        "Nexus 7",
        "31",
        "31, Phone",
        "Pixel 6",
        "Pixel 6a",
        "32",
        "32, Phone",
        "Pixel 5",
        "33",
        "33, Phone",
        "Pixel 7"
      )
    assertThat(table.header.columnModel.columnList.map { it.headerValue })
      .containsExactly("Name", "Status", "Actions")

    table.removeGrouping(Api)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "Tablet",
        "Nexus 7",
        "Nexus 7",
        "Phone",
        "Pixel 5",
        "Pixel 6",
        "Pixel 6a",
        "Pixel 7"
      )
    assertThat(table.header.columnModel.columnList.map { it.headerValue })
      .containsExactly("Name", "Api", "Status", "Actions")

    table.addGrouping(Status)
    assertThat(table.header.columnModel.columnList.map { it.headerValue })
      .containsExactly("Name", "Api", "Status", "Actions")
  }

  private fun RowComponent<CategoryTableDemo.Device>.stringValue() =
    when (this) {
      is CategoryRowComponent -> path.joinToString { it.value.toString() }
      is ValueRowComponent -> (componentList[0].component as JLabel).text
    }

  @Test
  fun tableLayout() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    scrollPane.setBounds(0, 0, 800, 400)
    fakeUi.layout()

    // All columns have fixed width except the first, which should absorb the extra width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(410, 80, 80, 80, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 500, 400)
    fakeUi.layout()

    // We are space-constrained, take some space away from each element (except the last, which is
    // fixed-size).
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(247, 34, 34, 34, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 410, 400)
    fakeUi.layout()

    // We have just enough width to give every component its minimum width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 200, 400)
    fakeUi.layout()

    // We don't have enough space for the minimum; the columns keep their width but the rendering is
    // truncated.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()
  }

  @Test
  fun hover() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    scrollPane.setBounds(0, 0, 400, 400)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    table.addOrUpdateRow(
      CategoryTableDemo.Device(
        "Copy 3 of Google Pixel 7 Pro API 34 arm64 Google Play",
        "34",
        "Phone",
        "Offline"
      ),
    )
    fakeUi.layout()

    // Hover over the first component.
    val firstRowCells =
      (table.rowComponents[0] as ValueRowComponent<*>).componentList.map { it.component }
    fakeUi.mouse.moveTo(convertPoint(firstRowCells[0], 5, 5, scrollPane))
    fakeUi.layout()

    // It should expand to its preferred width.  Other components retain their normal width.
    assertThat(firstRowCells[0].preferredWidth).isGreaterThan(200)
    assertThat(firstRowCells.map { it.width })
      .containsExactly(firstRowCells[0].preferredWidth, 20, 20, 20, 150)
      .inOrder()

    // Move the mouse to the next cell.
    fakeUi.mouse.moveTo(convertPoint(firstRowCells[1], 5, 5, scrollPane))
    fakeUi.layout()

    // Its contents fit, so its width shouldn't be affected. The first cell should return to its
    // original size (even though we are still within its expanded-width bounds).
    assertThat(firstRowCells[1].preferredWidth).isLessThan(20)
    assertThat(firstRowCells.map { it.width }).containsExactly(200, 20, 20, 20, 150).inOrder()

    // Move the mouse back to the first cell to expand it, then move it mouse out of the table,
    // causing a MOUSE_EXITED event. It should return to original size.
    fakeUi.mouse.moveTo(convertPoint(firstRowCells[0], 5, 5, scrollPane))
    fakeUi.layout()
    fakeUi.mouse.moveTo(0, 0)
    fakeUi.layout()

    assertThat(firstRowCells.map { it.width }).containsExactly(200, 20, 20, 20, 150).inOrder()
  }

  @Test
  fun sorting() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    fakeUi.clickRelativeTo(scrollPane, 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING))

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.ASCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.DESCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )
  }

  @Test
  fun selection() {
    // Set some distinct colors
    val colors =
      CategoryTable.Colors(
        selectedForeground = JBColor.BLUE,
        selectedBackground = JBColor.WHITE,
        unselectedBackground = JBColor.RED,
        unselectedForeground = JBColor.GREEN,
      )
    val table = CategoryTable(CategoryTableDemo.columns, colors = colors)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }
    fakeUi.layout()

    assertThat(table.selection.selectedKeys()).isEmpty()

    val rowToSelect = table.rowComponents[0] as ValueRowComponent<CategoryTableDemo.Device>
    val nameColumn = table.columns.indexOf(Name)
    val nameColumnComponent = rowToSelect.componentList[nameColumn].component
    val actionColumn = table.columns.indexOf(Actions)
    val actionColumnComponent = rowToSelect.componentList[actionColumn].component
    val actionButton = actionColumnComponent.componentList[0]
    val originalButtonForeground = actionButton.foreground

    fakeUi.clickOn(rowToSelect)

    assertThat(table.selection.selectedKeys()).contains(rowToSelect.rowKey)
    assertThat(rowToSelect.isFocusOwner).isTrue()

    IdeEventQueue.getInstance().flushQueue()

    assertThat(nameColumnComponent.foreground).isEqualTo(colors.selectedForeground)
    assertThat(nameColumnComponent.background).isEqualTo(colors.selectedBackground)
    assertThat(actionColumnComponent.foreground).isEqualTo(colors.selectedForeground)
    assertThat(actionColumnComponent.background).isEqualTo(colors.selectedBackground)
    assertThat(actionButton.foreground).isEqualTo(originalButtonForeground)
  }

  @Test
  fun scrollSelection() {
    val table = CategoryTable(CategoryTableDemo.columns)
    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }

    val scrollPane = createScrollPane(table)
    scrollPane.setBounds(0, 0, 800, 100)
    val fakeUi = FakeUi(scrollPane)

    for (row in CategoryTableDemo.devices.indices) {
      table.selection.selectNextRow()
      fakeUi.layout()

      val y = scrollPane.viewport.viewPosition.y
      val visibleRange = Range.closed(y, y + scrollPane.height)
      val rowComponent = table.rowComponents[row]
      assertThat(rowComponent.y).isIn(visibleRange)
      assertThat(rowComponent.y + rowComponent.height).isIn(visibleRange)
    }
  }

  @Test
  fun collapsedRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }
    table.addGrouping(Status)

    fakeUi.layout()

    val categoryRow = table.rowComponents[0] as CategoryRowComponent<CategoryTableDemo.Device>
    val category2Row = table.rowComponents[4] as CategoryRowComponent<CategoryTableDemo.Device>
    val row2Position = fakeUi.getPosition(table.rowComponents[1])
    val category2Position = fakeUi.getPosition(category2Row)

    assertThat(table.rowComponents[1].isVisible).isTrue()

    val fullHeight = table.preferredSize.height

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()
    assertThat(table.rowComponents[2].isVisible).isFalse()
    assertThat(fakeUi.getPosition(category2Row)).isEqualTo(row2Position)
    assertThat(table.preferredSize.height).isLessThan(fullHeight)

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isTrue()
    assertThat(table.rowComponents[2].isVisible).isTrue()
    assertThat(fakeUi.getPosition(category2Row)).isEqualTo(category2Position)
  }

  @Test
  fun rowDataContext() {
    TestApplicationManager.getInstance()
    HeadlessDataManager.fallbackToProductionDataManager(disposableRule.disposable)

    val table =
      CategoryTable(
        CategoryTableDemo.columns,
        rowDataProvider = DefaultValueRowDataProvider(DEVICE_DATA_KEY)
      )

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }

    val component = (table.rowComponents[0] as ValueRowComponent).componentList[0].component
    val data = DataManager.getInstance().getDataContext(component).getData(DEVICE_DATA_KEY)

    assertThat(data).isEqualTo(CategoryTableDemo.devices[0])
  }

  @Test
  fun hiddenRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }
    fakeUi.layout()

    val position = fakeUi.getPosition(table.rowComponents[1])

    table.setRowVisibleByKey(CategoryTableDemo.devices[1], false)
    fakeUi.layout()

    assertThat(fakeUi.getPosition(table.rowComponents[2])).isEqualTo(position)

    // Clicking the row that shares the location of the hidden row should select
    // the visible row
    fakeUi.clickOn(table.rowComponents[2])
    assertThat(table.selection.selectedKeys()).containsExactly(table.rowComponents[2].rowKey)

    table.setRowVisibleByKey(CategoryTableDemo.devices[1], true)
    fakeUi.layout()

    assertThat(fakeUi.getPosition(table.rowComponents[2])).isNotEqualTo(position)
  }

  @Test
  fun hiddenCollapsedRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }
    table.addGrouping(Status)
    fakeUi.layout()

    val categoryRow = table.rowComponents[0] as CategoryRowComponent<CategoryTableDemo.Device>
    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()

    // Setting visibility of a collapsed row does not make it visible
    val value = (table.rowComponents[1] as ValueRowComponent<CategoryTableDemo.Device>).value
    table.setRowVisibleByKey(value, true)

    assertThat(table.rowComponents[1].isVisible).isFalse()

    // Un-collapsing an invisible row does not make it visible
    table.setRowVisibleByKey(value, false)

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()
    assertThat(table.rowComponents[2].isVisible).isTrue()
  }

  @Test
  fun scrollbarLayout() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)
    CategoryTableDemo.devices.forEach { table.addOrUpdateRow(it) }

    scrollPane.setBounds(0, 0, 800, 400)
    assertThat(scrollPane.verticalScrollBar.isVisible).isFalse()
    val widthWithoutScrollbar = table.width

    scrollPane.setBounds(0, 0, 800, 100)
    fakeUi.layout()
    assertThat(scrollPane.verticalScrollBar.isVisible).isTrue()
    assertThat(scrollPane.verticalScrollBar.width).isGreaterThan(0)
    assertThat(table.width).isEqualTo(widthWithoutScrollbar - scrollPane.verticalScrollBar.width)
  }

  @Test
  fun emptyStatePanel() {
    val emptyStatePanel = EmptyStatePanel("No devices")
    val table = CategoryTable(CategoryTableDemo.columns, { it.name }, emptyStatePanel = emptyStatePanel)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    assertThat(emptyStatePanel.isVisible).isTrue()

    table.addOrUpdateRow(CategoryTableDemo.devices[0])

    assertThat(emptyStatePanel.isVisible).isFalse()

    table.removeRow(CategoryTableDemo.devices[0])

    assertThat(emptyStatePanel.isVisible).isTrue()
  }
}
