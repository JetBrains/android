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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.ui.JListSimpleColoredComponent
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class VitalsConnectionSelectorPopup(
  selection: Selection<VitalsConnection>,
  private val scope: CoroutineScope,
  private val onSelect: (VitalsConnection) -> Unit
) : JPanel(BorderLayout()) {

  private val searchTextField = SearchTextField(false)

  init {
    val (mainConnections, secondaryConnections) = selection.items.partition { it.isPreferred }

    val contentPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    if (mainConnections.isNotEmpty()) {
      val suggestedContainer = JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(5) }
      val suggestedLabel =
        SimpleColoredComponent().apply {
          append("Suggested apps", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      val (suggestedApps, suggestedAppsModel) = setUpList()

      suggestedAppsModel.addAll(mainConnections)

      suggestedContainer.add(suggestedLabel, BorderLayout.NORTH)
      suggestedContainer.add(suggestedApps, BorderLayout.CENTER)

      contentPanel.add(suggestedContainer)

      if (secondaryConnections.isNotEmpty()) {
        suggestedContainer.border = JBUI.Borders.customLineBottom(JBColor.border())
      }
    }

    if (secondaryConnections.isNotEmpty()) {
      val allContainer = JPanel(BorderLayout()).apply { JBUI.Borders.empty(5) }
      val allLabel =
        SimpleColoredComponent().apply {
          append("All apps", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      val (allApps, allAppsModel) = setUpList()
      allAppsModel.addAll(secondaryConnections)
      allContainer.add(allLabel, BorderLayout.NORTH)
      allContainer.add(allApps, BorderLayout.CENTER)

      contentPanel.add(allContainer)
    }

    if (mainConnections.isEmpty() && secondaryConnections.isEmpty()) {
      val emptyMessagePanel = JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(5) }
      val title = JBLabel("No apps available").apply { border = JBUI.Borders.emptyBottom(5) }
      val subtitle =
        JBLabel("Your Play Console account does not have access to").apply {
          foreground = JBColor.GRAY
        }
      val subtitleLine2 = JBLabel("Android Vitals for any app.").apply { foreground = JBColor.GRAY }
      emptyMessagePanel.add(title, BorderLayout.NORTH)
      emptyMessagePanel.add(subtitle, BorderLayout.CENTER)
      emptyMessagePanel.add(subtitleLine2, BorderLayout.SOUTH)
      contentPanel.add(emptyMessagePanel)
    }

    add(searchTextField, BorderLayout.NORTH)
    add(contentPanel, BorderLayout.CENTER)
  }

  private fun setUpList(): Pair<JBList<VitalsConnection>, FilteringListModel<VitalsConnection>> {
    val model = FilteringListModel(CollectionListModel(emptyList<VitalsConnection>()))
    val connectionsList = JBList(model)
    model.setFilter { connection ->
      connection.displayName.contains(searchTextField.text, ignoreCase = true) ||
        connection.appId.contains(searchTextField.text, ignoreCase = true)
    }
    connectionsList.selectionMode = ListSelectionModel.SINGLE_SELECTION

    searchTextField.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          model.refilter()
        }
      }
    )

    connectionsList.setCellRenderer { list, value, _, _, _ ->
      val hasFocus = list.selectedValue == value
      val renderer = JPanel(BorderLayout())
      renderer.isOpaque = false
      renderer.border = JBUI.Borders.empty(2, 5)
      val component =
        JListSimpleColoredComponent(null, list, hasFocus).apply {
          toolTipText = value.appId
          append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          append("  ")
          append(value.appId, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
      component.background = if (hasFocus) list.selectionBackground else list.background
      renderer.add(component, BorderLayout.WEST)
      renderer
    }
    connectionsList.addMouseListener(
      object : MouseAdapter() {
        override fun mouseExited(e: MouseEvent) {
          connectionsList.clearSelection()
        }

        override fun mouseClicked(e: MouseEvent) {
          connectionsList.selectedValue?.let { onSelect(it) }
        }
      }
    )
    connectionsList.addMouseMotionListener(
      object : MouseMotionAdapter() {
        override fun mouseMoved(e: MouseEvent) {
          val index = connectionsList.locationToIndex(e.point)
          if (index != -1) {
            connectionsList.selectedIndex = index
          } else {
            connectionsList.clearSelection()
          }
        }
      }
    )
    connectionsList.addKeyListener(
      object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) {
          searchTextField.requestFocusInWindow()
          scope.launch(AndroidDispatchers.uiThread) { searchTextField.dispatchEvent(e) }
        }
      }
    )

    return connectionsList to model
  }

  fun asPopup() =
    JBPopupFactory.getInstance()
      .createComponentPopupBuilder(this, searchTextField)
      .setFocusable(true)
      .setRequestFocus(true)
      .createPopup()
}
