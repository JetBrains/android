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
package com.android.tools.idea.sqlite.ui.sqliteEvaluator

import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent

/**
 * Class responsible for showing a popup containing a list of previously executed statements.
 *
 * When the selection model of the list changes, the selected item is temporarily shown in
 * [editorTextField]. When the user press enter and an item is selected in the list, the text of
 * that item is permanently shown in [editorTextField] and the popup is closed. If the popup is
 * closed without enter being pressed (esc or click outside), the text originally shown in
 * [editorTextField] is restored.
 */
class QueryHistoryView(private val editorTextField: EditorTextField) {
  private val listModel = DefaultListModel<String>()
  private val list = JBList(listModel)

  private val panel = JPanel(BorderLayout())
  private val hint = LightweightHint(panel)
  val component: JComponent = hint.component

  private var editorPermanentQuery = ""

  /**
   * if [shouldRestorePermanentQuery] is true, [editorPermanentQuery] should be restored when the
   * hint popup is closed.
   */
  private var shouldRestorePermanentQuery = true

  init {
    hint.setForceShowAsPopup(true)
    hint.setCancelOnClickOutside(true)
    hint.setResizable(true)
    hint.setFocusRequestor(list)
    hint.addHintListener {
      // called when hint is dismissed (by pressing esc on keyboard or calling hint.hide)
      editorTextField.requestFocusInWindow()
    }

    list.emptyText.isShowAboveCenter = false
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = MyListCellRenderer()
    list.addFocusListener(
      object : FocusListener {
        override fun focusGained(e: FocusEvent) {}

        override fun focusLost(e: FocusEvent) {
          if (shouldRestorePermanentQuery) {
            editorTextField.text = editorPermanentQuery
          }
          editorTextField.editor?.selectionModel?.setSelection(0, 0)
          list.clearSelection()
        }
      }
    )

    list.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
      if (e.valueIsAdjusting) return@addListSelectionListener

      val viewIndex = list.selectionModel.minSelectionIndex
      if (viewIndex < 0) return@addListSelectionListener

      val query = listModel.get(viewIndex)
      editorTextField.text = query
      editorTextField.editor?.selectionModel?.setSelection(0, query.length)
    }

    list.addKeyListener(
      object : KeyListener {
        override fun keyTyped(e: KeyEvent) {}

        override fun keyPressed(e: KeyEvent) {}

        override fun keyReleased(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ENTER) {
            selectEntryAndCloseQueryHistory()
          }
        }
      }
    )

    list.addMouseMotionListener(
      object : MouseMotionListener {
        override fun mouseDragged(e: MouseEvent) {}

        override fun mouseMoved(e: MouseEvent) {
          val viewIndex = list.locationToIndex(e.point)
          if (viewIndex < 0) return
          list.selectedIndex = viewIndex
        }
      }
    )

    val doubleClickListener =
      object : DoubleClickListener() {
        override fun onDoubleClick(event: MouseEvent): Boolean {
          selectEntryAndCloseQueryHistory()
          return false
        }
      }

    doubleClickListener.installOn(list)

    val listScroller = JBScrollPane(list)
    listScroller.border = JBUI.Borders.empty()
    val instructionsLabel = JBLabel("Press Enter to insert")
    instructionsLabel.font = instructionsLabel.font.deriveFont(11f)
    instructionsLabel.border = JBUI.Borders.empty(2, 8, 2, 8)
    instructionsLabel.isEnabled = false

    panel.add(listScroller, BorderLayout.CENTER)
    panel.add(instructionsLabel, BorderLayout.SOUTH)
    list.fixedCellWidth = JBUI.scale(350)
  }

  fun show(component: JComponent, x: Int, y: Int) {
    editorPermanentQuery = editorTextField.text
    shouldRestorePermanentQuery = true
    hint.show(component, x, y, editorTextField, HintHint())
  }

  fun setQueryHistory(queries: List<String>) {
    listModel.clear()
    queries.forEach { listModel.addElement(it) }
  }

  private fun selectEntryAndCloseQueryHistory() {
    val selectedViewIndex = list.selectionModel.minSelectionIndex
    if (selectedViewIndex < 0) return

    val query = listModel.get(selectedViewIndex)
    editorTextField.text = query
    shouldRestorePermanentQuery = false
    hint.hide()
  }

  private class MyListCellRenderer : ColoredListCellRenderer<String>() {
    override fun customizeCellRenderer(
      list: JList<out String>,
      value: String,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      append("${index + 1}.")
      append("  ")
      append(value)
    }
  }
}
