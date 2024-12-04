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
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val VITALS_POPUP_ITEM_BORDER = JBUI.Borders.empty(1, 8)

class VitalsConnectionSelectorPopup(
  selection: Selection<VitalsConnection>,
  private val scope: CoroutineScope,
  private val onSelect: (VitalsConnection) -> Unit,
) : JPanel(BorderLayout()) {

  private val searchTextField = SearchTextField(false)

  init {
    val (mainConnections, secondaryConnections) = selection.items.partition { it.isPreferred }
    val contentPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    if (mainConnections.isEmpty() && secondaryConnections.isEmpty()) {
      val bannerPanel =
        JPanel(BorderLayout()).apply {
          border = JBUI.Borders.empty(8, 8, 0, 8)
          add(NoAvailableAppsBanner(), BorderLayout.CENTER)
        }
      contentPanel.add(bannerPanel)
    }

    val suggestedContainer =
      JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(5, 5, 0, 5) }
    val suggestedLabel =
      SimpleColoredComponent().apply {
        append("Suggested apps for this project", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        border = VITALS_POPUP_ITEM_BORDER
      }
    val (suggestedApps, suggestedAppsModel) = setUpList()

    suggestedAppsModel.addAll(mainConnections)

    suggestedContainer.add(suggestedLabel, BorderLayout.NORTH)
    suggestedContainer.add(
      if (mainConnections.isEmpty()) {
        emptyLabel("No suggested apps")
      } else {
        suggestedApps
      },
      BorderLayout.CENTER,
    )

    contentPanel.add(suggestedContainer)

    val separatorPanel =
      JPanel(BorderLayout()).apply {
        add(JSeparator(), BorderLayout.CENTER)
        border = JBUI.Borders.empty(0, 8)
      }
    contentPanel.add(separatorPanel)

    val allContainer = JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(0, 5) }
    val allLabel =
      SimpleColoredComponent().apply {
        append(
          "${if (mainConnections.isNotEmpty()) "Other" else "All"} apps",
          SimpleTextAttributes.GRAYED_ATTRIBUTES,
        )
        border = VITALS_POPUP_ITEM_BORDER
      }
    val (allApps, allAppsModel) = setUpList()
    allAppsModel.addAll(secondaryConnections)
    allContainer.add(allLabel, BorderLayout.NORTH)
    allContainer.add(
      if (secondaryConnections.isEmpty()) {
        emptyLabel("No apps accessible to you")
      } else {
        allApps
      },
      BorderLayout.CENTER,
    )

    contentPanel.add(allContainer)

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
      renderer.border = VITALS_POPUP_ITEM_BORDER
      val component =
        JListSimpleColoredComponent(null, list, hasFocus).apply {
          toolTipText = value.appId
          append(value.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          append("  ")
          append(value.appId, SimpleTextAttributes.GRAYED_ATTRIBUTES)
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

    connectionsList.border = JBUI.Borders.empty()
    return connectionsList to model
  }

  fun asPopup() =
    JBPopupFactory.getInstance()
      .createComponentPopupBuilder(this, searchTextField)
      .setFocusable(true)
      .setRequestFocus(true)
      .setMinSize(Dimension(preferredWidth, preferredHeight + 20))
      .createPopup()

  private fun emptyLabel(text: String) =
    SimpleColoredComponent().apply {
      append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      border = VITALS_POPUP_ITEM_BORDER
    }

  inner class NoAvailableAppsBanner : JPanel(BorderLayout()) {
    init {
      isOpaque = false
      border = JBUI.Borders.empty(8)
      val iconPanel =
        JPanel(BorderLayout()).apply {
          val iconLabel = JBLabel(AllIcons.General.Error).apply { isOpaque = false }
          add(iconLabel, BorderLayout.NORTH)
          isOpaque = false
        }
      add(iconPanel, BorderLayout.WEST)

      val descriptionTextPane =
        JTextArea().apply {
          text = "Your Play Console account does not have access to Android Vitals for any app."
          isEditable = false
          isFocusable = false
          wrapStyleWord = true
          lineWrap = true
          columns = 25
          font = JBFont.label()
          isOpaque = false
          border = JBUI.Borders.emptyLeft(4)
        }
      add(descriptionTextPane, BorderLayout.CENTER)
    }

    override fun paintBorder(g: Graphics) {
      super.paintComponent(g)
      with(g as Graphics2D) {
        val color = Banner.ERROR_BACKGROUND
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = color
        g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12)
        g.color = color.brighter()
        g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
      }
    }
  }
}
