/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui

import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.adtui.common.AdtUiUtils
import com.intellij.icons.AllIcons
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.plaf.metal.MetalButtonUI

private val DEFAULT_BORDER = RoundedLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 2, 1)

/**
 * Setup the given button with the L&F for the arrow buttons (sizes, colors, etc)
 */
private fun setupArrowUI(button: JButton, newBorder: Border, icon: Icon): JButton =
  button.apply {
    // Use the MetalButtonIU to have a consistent UI across platforms
    ui = MetalButtonUI()
    isContentAreaFilled = false
    isFocusPainted = false
    isOpaque = false
    minimumSize = JBUI.size(icon.iconWidth + 10)
    preferredSize = JBUI.size(icon.iconWidth + 10)
    border = newBorder
  }

/**
 * Interface to be implemented by a custom cell renderer for [HorizontalSpinner]
 */
interface HorizontalSpinnerCellRenderer<T> {
  fun getCellRendererComponent(
    list: HorizontalSpinner<T>,
    value: T,
    index: Int
  ): Component
}

/**
 * Default cell renderer that displays a JLabel with the content returned by the value [Object#toString]
 */
class DefaultRenderer<T> : HorizontalSpinnerCellRenderer<T> {
  private val label = JLabel("", SwingConstants.CENTER).apply {
    border = JBUI.Borders.empty(2, 5)
  }

  override fun getCellRendererComponent(
    list: HorizontalSpinner<T>,
    value: T,
    index: Int
  ): Component = label.apply { text = value.toString() }
}

/**
 * Component that displays one element at a time from a given list. Two buttons are displayed on the sides
 */
class HorizontalSpinner<T> private constructor(_model: ListModel<T>) : AdtPrimaryPanel(BorderLayout(0, 0)) {
  companion object {
    @JvmStatic
    fun <T> forModel(model: ListModel<T>): HorizontalSpinner<T> = HorizontalSpinner(model)

    @JvmStatic
    fun forStrings(strings: Array<String>): HorizontalSpinner<String> {
      val model = object : AbstractListModel<String>() {
        override fun getElementAt(index: Int): String = strings[index]
        override fun getSize(): Int = strings.size

      }
      return forModel<String>(model)
    }
  }

  /**
   * Cell renderer implementing [HorizontalSpinnerCellRenderer]
   */
  var cellRenderer: HorizontalSpinnerCellRenderer<T> = DefaultRenderer()

  /**
   * Model containing the list of elements
   */
  var model: ListModel<T> = _model
    set(value) {
      field.removeListDataListener(dataListener)
      field = value
      onNewModel()
    }

  /**
   * Index of the element currently selected in the list or -1 if the list is empty
   */
  var selectedIndex: Int
    set(value) {
      if (value < 0 || value > model.size - 1) {
        throw IndexOutOfBoundsException()
      }

      innerSetSelectedIndex(value)
    }
    get() = _selectedIndex

  private var _selectedIndex: Int = -1
  private val leftButton = JButton(AllIcons.Actions.Left)
  private val rightButton = JButton(AllIcons.Actions.Right)
  private var cell: Component? = null
  private val dataListener = object : ListDataListener {
    override fun contentsChanged(e: ListDataEvent?) = innerSetSelectedIndex(_selectedIndex)
    override fun intervalRemoved(e: ListDataEvent?) = innerSetSelectedIndex(_selectedIndex)
    override fun intervalAdded(e: ListDataEvent?) = innerSetSelectedIndex(_selectedIndex)
  }

  init {
    border = DEFAULT_BORDER

    setupArrowUI(leftButton, AdtUiUtils.DEFAULT_RIGHT_BORDER, AllIcons.Actions.Left)
      .addActionListener { innerSetSelectedIndex(_selectedIndex - 1) }
    setupArrowUI(rightButton, AdtUiUtils.DEFAULT_LEFT_BORDER, AllIcons.Actions.Right)
      .addActionListener { innerSetSelectedIndex(_selectedIndex + 1) }

    add(leftButton, BorderLayout.LINE_START)
    add(rightButton, BorderLayout.LINE_END)

    onNewModel()
  }

  private fun onModelUpdate() {
    val empty = model.size == 0
    leftButton.isEnabled = !empty
    rightButton.isEnabled = !empty

    // Remove the previous cell and add a new one with the new value
    if (cell != null) {
      remove(cell)
      cell = null
    }

    if (!empty) {
      cell = cellRenderer.getCellRendererComponent(this, model.getElementAt(selectedIndex), selectedIndex)
      add(cell, BorderLayout.CENTER)
    }
    revalidate()
    repaint()

    val listeners = listenerList.listenerList
    val e = ListSelectionEvent(this, 0, model.size, false)
    listeners
      .filterIsInstance<ListSelectionListener>()
      .forEach {
        it.valueChanged(e)
      }
  }

  private fun onNewModel() {
    model.addListDataListener(dataListener)
    if (model.size > 0 && _selectedIndex == -1) {
      // There was no element selected, select the first one
      _selectedIndex = 0
    }

    // The model has changed so try call innerSetSelectedIndex to update the selected position and the UI
    innerSetSelectedIndex(selectedIndex, true)
  }

  /**
   * Sets the current selected index truncating the value between -1 and the max list size. -1 is used when the list
   * if empty and no element is selected.
   */
  private fun innerSetSelectedIndex(requestedIndex: Int, forceUpdate: Boolean = false) {
    val newIndex = if (model.size != 0) {
      if (requestedIndex < 0) {
        model.size - 1 // Out of lower bound, set to the max
      } else {
        requestedIndex % model.size
      }
    } else {
      -1
    }

    val notifyUpdate = forceUpdate || newIndex != _selectedIndex
    _selectedIndex = newIndex

    if (notifyUpdate) {
      onModelUpdate()
    }
  }

  /**
   * Returns true if the given index is the currently selected index
   */
  fun isSelectedIndex(index: Int) = selectedIndex == index

  fun addListSelectionListener(listener: ListSelectionListener) =
    listenerList.add(ListSelectionListener::class.java, listener)

  fun removeListSelectionListener(listener: ListSelectionListener) =
    listenerList.remove(ListSelectionListener::class.java, listener)
}
