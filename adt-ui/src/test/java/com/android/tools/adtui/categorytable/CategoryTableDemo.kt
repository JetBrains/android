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

import com.android.tools.adtui.categorytable.Attribute.Companion.stringAttribute
import com.android.tools.adtui.categorytable.CategoryTableDemo.Device
import com.android.tools.adtui.categorytable.Column.SizeConstraint
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

object CategoryTableDemo {
  data class Device(val name: String, val api: String, val type: String, val status: String) {}

  val devices =
    listOf(
      Device("Pixel 5", "32", "Phone", "Offline"),
      Device("Pixel 6", "31", "Phone", "Offline"),
      Device("Pixel 6a", "31", "Phone", "Online"),
      Device("Pixel 7", "33", "Phone", "Online"),
      Device("Nexus 7", "25", "Tablet", "Offline"),
      Device("Nexus 7", "26", "Tablet", "Online")
    )
  val columns = listOf(Name, Api, Status, Type, Actions)
}

val DEVICE_DATA_KEY = DataKey.create<Device>("DEVICE")

val Name =
  LabelColumn<Device>(
    "Name",
    SizeConstraint(min = 200, preferred = 400),
    stringAttribute(isGroupable = false) { it.name }
  )
val Api = LabelColumn<Device>("Api", SizeConstraint(min = 20, max = 80), stringAttribute { it.api })
val Type =
  LabelColumn<Device>("Type", SizeConstraint(min = 20, max = 80), stringAttribute { it.type })
val Status =
  object :
    LabelColumn<Device>(
      "Status",
      SizeConstraint(min = 20, max = 80),
      stringAttribute { it.status }
    ) {
    override val visibleWhenGrouped = true
  }

object Actions : Column<Device, Unit, JPanel> {
  override val name = "Actions"
  override val attribute = Attribute.Unit

  override fun createUi(rowValue: Device): JPanel =
    JPanel().apply {
      isOpaque = false
      add(
        JButton("Start").apply {
          addActionListener { JOptionPane.showMessageDialog(this, "Starting ${rowValue.name}") }
        }
      )
      add(
        JButton("Edit").apply {
          addActionListener { JOptionPane.showMessageDialog(this, "Editing ${rowValue.name}") }
        }
      )
    }

  override fun updateValue(rowValue: Device, component: JPanel, value: Unit) {}

  override val widthConstraint = SizeConstraint.exactly(150)
}

object DemoCategoryRowMouseClickListener : DefaultCategoryRowMouseClickListener<Device>() {
  override fun categoryRowClicked(
    e: MouseEvent,
    table: CategoryTable<Device>,
    path: CategoryList<Device>
  ) {
    when {
      SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2 ->
        table.columns
          .find { it.attribute == path.last().attribute }
          ?.let { table.removeGrouping(it) }
      else -> super.categoryRowClicked(e, table, path)
    }
  }
}

object DemoCategoryTableHeaderClickListener : DefaultCategoryTableHeaderClickListener<Device>() {
  override fun columnHeaderClicked(
    e: MouseEvent,
    table: CategoryTable<Device>,
    column: Column<Device, *, *>
  ) {
    when {
      SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2 ->
        if (column.attribute.isGroupable) {
          table.addGrouping(column)
        }
      else -> super.columnHeaderClicked(e, table, column)
    }
  }
}

fun main(args: Array<String>) {
  val frame = JFrame()
  frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

  val table =
    CategoryTable(CategoryTableDemo.columns).apply {
      categoryRowMouseClickListener = DemoCategoryRowMouseClickListener
      categoryTableHeaderClickListener = DemoCategoryTableHeaderClickListener
    }

  CategoryTableDemo.devices.forEach(table::addOrUpdateRow)

  val scroll = JBScrollPane()
  frame.contentPane = scroll
  table.addToScrollPane(scroll)

  table.addGrouping(Status)
  table.addGrouping(Api)
  table.addGrouping(Type)
  table.removeGrouping(Api)
  table.removeGrouping(Type)

  frame.preferredSize = Dimension(600, 800)
  frame.pack()
  frame.isVisible = true
}
