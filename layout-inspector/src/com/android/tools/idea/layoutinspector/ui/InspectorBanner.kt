/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import javax.swing.SwingUtilities
import javax.swing.event.TableModelEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel

private const val HORIZONTAL_BORDER_SIZE = 6
private const val VERTICAL_BORDER_SIZE = 3

/**
 * A banner for showing notifications in the Layout Inspector.
 */
class InspectorBanner(project: Project) : JPanel(BorderLayout()) {
  private val model = NotificationsModel()
  private val table = NotificationTable(model)
  private val bannerService = InspectorBannerService.getInstance(project)
  private var notifications: List<StatusNotification> = emptyList()

  init {
    isVisible = false
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    // The TableExpandableItemsHandler will not work unless the table is wrapped in a scroll pane:
    val scrollPane = ScrollPaneFactory.createScrollPane(table, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = JBUI.Borders.empty()
    add(scrollPane, BorderLayout.CENTER)
    bannerService?.notificationListeners?.add(::updateNotifications)
  }

  private fun updateNotifications() {
    // fire the data changed on the UI thread:
    invokeLater {
      notifications = bannerService?.notifications ?: emptyList()
      isVisible = notifications.isNotEmpty()
      model.fireTableDataChanged()
      // Update the InspectorBanner height:
      revalidate()
    }
  }

  /**
   * [TableModel] for displaying the notifications in a table.
   */
  private inner class NotificationsModel : AbstractTableModel() {
    override fun getRowCount(): Int = notifications.size

    override fun getColumnCount(): Int = 2

    override fun getValueAt(rowIndex: Int, columnIndex: Int) = notifications.getOrNull(rowIndex)

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 1
  }

  private class NotificationTable(model: NotificationsModel) : JBTable(model) {
    private val text = JLabel()
    private val actionLayout = FlowLayout(FlowLayout.RIGHT, JBUI.scale(HORIZONTAL_BORDER_SIZE), 0)
    private val actionPanel = JPanel(actionLayout)
    private val tableCellRenderer = TableCellRenderer { _, _, _, _, row, column ->
      getCellComponent(row, column)
    }
    private val mouseListener = object : MouseAdapter() {
      override fun mouseMoved(event: MouseEvent) = handleMouseMoved(event)
    }
    private var lastActionRenderRow = -1
    private var classInitialized = false

    init {
      classInitialized = true
      applyUISettings()
      tableHeader = null
      setShowGrid(false)
      // Update the table cursor with the cursor of the renderer
      addMouseMotionListener(mouseListener)
      // Disable hovered background: We would like to hide the fact that this is a table.
      @Suppress("UnstableApiUsage")
      putClientProperty(RenderingUtil.PAINT_HOVERED_BACKGROUND, false)
    }

    override fun updateUI() {
      super.updateUI()
      // This method is called from the initializer of JPanel, avoid NPE be delaying until this class is fully initialized.
      if (classInitialized) {
        applyUISettings()
      }
    }

    override fun tableChanged(event: TableModelEvent) {
      super.tableChanged(event)
      // The table was updated. Invalidate the renderer cache:
      lastActionRenderRow = -1
    }

    override fun doLayout() {
      // Make sure there is enough space for the action column:
      val messageColumn = getColumnModel().getColumn(0)
      val actionColumn = getColumnModel().getColumn(1)
      val maxActionWidth = getMaxActionWidth()
      actionColumn.width = minOf(width, maxActionWidth)
      messageColumn.width = width - actionColumn.width
    }

    override fun getCellRenderer(row: Int, column: Int) = tableCellRenderer

    /**
     * Forward mouse events to the renderer for the action column.
     * The implementation of HyperlinkLabel is dependent of several events for a mouse click to work.
     */
    override fun processMouseEvent(event: MouseEvent) {
      if (!forwardMouseEventToHoveredComponent(event)) {
        super.processMouseEvent(event)
      }
    }

    private fun applyUISettings() {
      val borderSpacing = JBUI.Borders.empty(VERTICAL_BORDER_SIZE, HORIZONTAL_BORDER_SIZE)
      val borderSeparator = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
      val renderBorder = JBUI.Borders.compound(borderSeparator, borderSpacing)
      text.border = renderBorder
      val globalScheme = EditorColorsManager.getInstance().globalScheme
      background = globalScheme.getColor(EditorColors.NOTIFICATION_BACKGROUND)
      actionPanel.border = renderBorder
      actionPanel.background = background
      actionLayout.hgap = JBUI.scale(VERTICAL_BORDER_SIZE)
    }

    /**
     * Returns the width of the largest action component
     */
    private fun getMaxActionWidth(): Int {
      var width = 0
      for (row in 0 until model.rowCount) {
        val actionWidth = getCellComponent(row, 1).preferredSize.width
        width = maxOf(width, actionWidth)
      }
      return width
    }

    private fun handleMouseMoved(event: MouseEvent) {
      // Update the mouse cursor when the mouse is moved over an active link
      cursor = findComponentAtMouse(event)?.cursor ?: Cursor.getDefaultCursor()
    }

    private fun forwardMouseEventToHoveredComponent(event: MouseEvent): Boolean {
      val component = findComponentAtMouse(event) ?: return false

      // The actionPanel must be a child of the table for the convertMouseEvent to function correctly:
      add(actionPanel)
      val newEvent = SwingUtilities.convertMouseEvent(this, event, component)
      // Remove the actionPanel after the computation
      remove(actionPanel)

      // Forward mouse clicks to the active link if any
      component.dispatchEvent(newEvent)
      return true
    }

    /**
     * Return the component from the renderer that is under the point of the mouse.
     */
    private fun findComponentAtMouse(event: MouseEvent): Component? {
      val row = rowAtPoint(event.point)
      val column = columnAtPoint(event.point)
      if (column != 1) {
        return null // We are not interested in the message column
      }
      val renderer = getCellComponent(row, 1)
      val rect = getCellRect(row, column, true)
      renderer.bounds = rect
      TreeWalker(renderer).descendantStream().forEach(Component::doLayout)
      return SwingUtilities.getDeepestComponentAt(renderer, event.x - rect.x, event.y - rect.y)
    }

    private fun getCellComponent(row: Int, column: Int): Component {
      val notificationValue = model.getValueAt(row, 1) as? StatusNotification
      return if (column == 0) {
        text.apply { text = notificationValue?.message ?: "" }
      }
      else {
        if (lastActionRenderRow != row) {
          lastActionRenderRow = row
          actionPanel.removeAll()
          notificationValue?.let { notification ->
            notification.actions.forEach { action ->
              @Suppress("DialogTitleCapitalization")
              val actionLabel = HyperlinkLabel(action.templateText, JBColor.BLUE)
              actionLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
              actionLabel.addHyperlinkListener {
                val context = DataContext { dataId -> if (NOTIFICATION_KEY.`is`(dataId)) notification else null }
                val presentation = action.templatePresentation.clone()
                val event = AnActionEvent(it.inputEvent, context, ActionPlaces.NOTIFICATION, presentation, ActionManager.getInstance(), 0)
                action.actionPerformed(event)
              }
              actionPanel.add(actionLabel)
            }
          }
        }
        actionPanel
      }
    }
  }
}
