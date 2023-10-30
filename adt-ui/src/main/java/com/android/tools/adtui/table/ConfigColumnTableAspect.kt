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
package com.android.tools.adtui.table

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_POPUP
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.MNEMONICS
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn

/**
 * Manages Column Configuration of a JTable
 *
 * Adds the ability to manage the tables' column configuration in a persistable configuration.
 *
 * The call passes in a configuration as `MutableList<ColumnInfo>` which is updated as columns are
 * hidden, shown, resized and moved.
 *
 * Usage:
 * ```
 *    ConfigColumnTableAspect.apply(table, config)
 * ```
 */
class ConfigColumnTableAspect
private constructor(private val table: JTable, private val config: MutableList<ColumnInfo>) {

  /** A persistable representation of a table column configuration */
  @Tag("column-info")
  data class ColumnInfo(
    /** Display name of the column. Must match the table model. */
    @Attribute("name") var name: String = "",

    /** Width of the column relative to the width of the table */
    @Attribute("width-ratio") var widthRatio: Double = 0.0,

    /** Visibility of the column */
    @Attribute("visible") var visible: Boolean = true,
  )

  private val tcm
    get() = table.columnModel

  private val allColumns =
    tcm.columns.asSequence().associateByTo(LinkedHashMap()) { it.headerValue }
  private val configColumns = config.associateBy { it.name }
  private val columnModelListener = ColumnModelListener()
  private var initialUpdateDone = false

  companion object {
    fun apply(table: JTable, config: MutableList<ColumnInfo>) {
      ConfigColumnTableAspect(table, config)
    }
  }

  init {
    validateConfig()

    // Add `columnModelListener` first so that `withoutColumnModelListener` works properly
    tcm.addColumnModelListener(columnModelListener)

    applyConfiguration()

    table.addComponentListener(TableResizedListener())
    table.tableHeader.addMouseListener(ColumnSelectionMenuLauncher())
  }

  /** Validates that the configuration matches the column model of the table */
  private fun validateConfig() {
    val modelCols = tcm.columns.toList().map { it.headerValue }
    val configCols = config.map { it.name }

    if (configCols.size != modelCols.size || !configCols.toSet().containsAll(modelCols)) {
      throw IllegalArgumentException(
        "Configuration doesn't match model. Config columns=$configCols modelColumns=$modelCols"
      )
    }
  }

  /** Applies the column configuration to the table */
  private fun applyConfiguration() {
    withoutColumnModelListener {
      allColumns.values.forEach { table.removeColumn(it) }
      config
        .filter { it.visible }
        .forEach {
          val tableColumn = allColumns[it.name] ?: return@forEach
          table.addColumn(tableColumn)
        }
    }
  }

  private fun showColumnSelectionMenu(e: MouseEvent) {
    if (!e.isPopupTrigger) {
      return
    }

    val header = table.tableHeader
    val selected = tcm.getColumn(header.columnAtPoint(e.getPoint())).headerValue.toString()
    val actions = allColumns.values.map { ToggleColumnAction(it.getConfigColumn()) }
    val group = DefaultActionGroup(actions)

    val popupFactory = JBPopupFactory.getInstance()
    val popup = popupFactory.createActionGroupPopup(group) { it.columnInfo.name == selected }

    // Arbitrary spacing for visual clarity - use half the height of the header.
    val space = header.height / 2
    popup.showInScreenCoordinates(header, Point(e.xOnScreen - space, e.yOnScreen + space))
  }

  /** Execute code without triggering our [TableColumnModelListener] */
  private fun withoutColumnModelListener(block: () -> Unit) {
    tcm.removeColumnModelListener(columnModelListener)
    try {
      block()
    } finally {
      tcm.addColumnModelListener(columnModelListener)
    }
  }

  /** Creates a simple ListPopup of [ToggleColumnAction] with a preselected item */
  private fun JBPopupFactory.createActionGroupPopup(
    actionGroup: ActionGroup,
    isPreSelected: (ToggleColumnAction) -> Boolean
  ) =
    createActionGroupPopup(
      null,
      actionGroup,
      EMPTY_CONTEXT,
      MNEMONICS,
      false,
      null,
      -1,
      { isPreSelected(it as ToggleColumnAction) },
      TOOLWINDOW_POPUP
    )

  private fun TableColumn.getConfigColumn() = configColumns.getValue(headerValue.toString())

  /** Tracks changes to the column configuration and updates the config. */
  private inner class ColumnModelListener : TableColumnModelListener {
    override fun columnAdded(e: TableColumnModelEvent) {}

    override fun columnRemoved(e: TableColumnModelEvent) {}

    override fun columnMoved(e: TableColumnModelEvent) {
      if (e.fromIndex == e.toIndex) {
        return
      }
      val columns = tcm.columns.toList()

      // When a column is moved, we have the `from` & `to` of the columns in the `tcm`. These
      // indices do not match the indices of the configuration. Mapping the indices is done based on
      // their text. We find the text corresponding to the model index and then find the index of
      // that text in the configuration.
      val fromIndex = config.indexOfFirst { it.name == columns[e.toIndex].headerValue }

      // First remove the column from the `from` position
      val columnInfo = config.removeAt(fromIndex)

      // Handling the `to` index is more complicated. We need to find the index of the column that
      // comes right after the index we are given. This is because that's the insertion position. We
      // also need to handle the special case of moving a column to the last position.
      val toIndex =
        when {
          e.toIndex == columns.size - 1 -> config.size
          else -> config.indexOfFirst { it.name == columns[e.toIndex + 1].headerValue }
        }

      // Insert the column in the `to` position
      config.add(toIndex, columnInfo)
    }

    override fun columnMarginChanged(e: ChangeEvent) {
      if (initialUpdateDone) {
        val width = table.width
        tcm.columns.asSequence().forEach {
          val columnInfo = configColumns[it.headerValue] ?: return@forEach
          columnInfo.widthRatio = it.width.toDouble() / width
        }
      }
    }

    override fun columnSelectionChanged(e: ListSelectionEvent) {}
  }

  /**
   * Resizes columns when the table is resized
   *
   * After this is called for the first time, sets [initialUpdateDone] to `true`
   */
  private inner class TableResizedListener : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      withoutColumnModelListener {
        allColumns.values.forEach {
          val configColumn = configColumns[it.headerValue] ?: return@forEach
          it.preferredWidth = (table.width * configColumn.widthRatio).toInt()
        }
      }
      initialUpdateDone = true
    }
  }

  /** A [MouseAdapter] that launches the column selection menu */
  private inner class ColumnSelectionMenuLauncher : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      showColumnSelectionMenu(e)
    }

    override fun mouseReleased(e: MouseEvent) {
      showColumnSelectionMenu(e)
    }
  }

  /** Toggles the visibility of a column */
  private inner class ToggleColumnAction(val columnInfo: ColumnInfo) :
    ToggleAction(columnInfo.name) {

    override fun getActionUpdateThread() = BGT

    override fun isSelected(e: AnActionEvent) = columnInfo.visible

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      columnInfo.visible = state
      applyConfiguration()
    }
  }
}
