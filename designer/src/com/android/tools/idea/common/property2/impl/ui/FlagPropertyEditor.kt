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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.FlagPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.SearchTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * Editor for a flags property.
 *
 * Displays as a button with the current value displayed.
 * Clicking the button will bring up a balloon control where the individual flags
 * can be changed.
 */
class FlagPropertyEditor(val editorModel: FlagPropertyEditorModel) : AdtSecondaryPanel(BorderLayout()) {
  private val button = JButton()

  init {
    add(button, BorderLayout.CENTER)
    registerKeyAction({ editorModel.enterKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    registerKeyAction({ editorModel.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    registerKeyAction({ editorModel.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")
    addFocusListener(EditorFocusListener(editorModel, { "" }))
    button.addActionListener({ buttonPressed() })
    editorModel.addListener(ValueChangedListener { handleValueChanged() })
    handleValueChanged()
  }

  private fun buttonPressed() {
    editorModel.buttonPressed()
    val panel = FlagPropertyPanel(editorModel)

    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(panel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .createBalloon()

    panel.balloon = balloon
    balloon.show(RelativePoint.getCenterOf(button), Balloon.Position.below)
  }

  private fun handleValueChanged() {
    button.text = editorModel.buttonText
    isVisible = editorModel.visible
    toolTipText = editorModel.tooltip
    if (editorModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }
}

/**
 * A panel to be displayed in a balloon control.
 */
class FlagPropertyPanel(val editorModel: FlagPropertyEditorModel) : AdtSecondaryPanel(VerticalLayout(JBUI.scale(2))) {
  var balloon: Balloon? = null
  private val innerPanel = AdtSecondaryPanel(VerticalLayout(JBUI.scale(2)))
  private val flagDivider = JSeparator()

  init {
    addLinks()
    addSearchField()
    addAllCheckBoxes()
    add(createScrollPane(innerPanel))
    add(JSeparator())
    addApplyButton()
    handleValueChanged()

    editorModel.addListener(ValueChangedListener { handleValueChanged() })
  }

  private fun addLinks() {
    val font = UIUtil.getLabelFont()
    val smallerFont = font.deriveFont(font.size * 0.7f)
    val selectAll = HyperlinkLabel("Select all")
    selectAll.font = smallerFont
    selectAll.addHyperlinkListener { editorModel.selectAll() }
    val clearAll = HyperlinkLabel("Clear all")
    clearAll.font = smallerFont
    clearAll.addHyperlinkListener { editorModel.clearAll() }
    val links = AdtSecondaryPanel(BorderLayout())
    links.font = smallerFont
    links.border = JBUI.Borders.empty(5)
    links.add(selectAll, BorderLayout.WEST)
    links.add(clearAll, BorderLayout.CENTER)
    add(links, VerticalLayout.TOP)
  }

  private fun addSearchField() {
    val searchField = SearchTextField()
    add(searchField)
    searchField.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(event: DocumentEvent) {
            editorModel.filter = searchField.text.trim { it <= ' ' }
          }
        }
    )
  }

  private fun createScrollPane(component: JComponent): JScrollPane {
    val scrollPane = JBScrollPane(component)
    scrollPane.border = JBUI.Borders.empty()
    return scrollPane
  }

  private fun addAllCheckBoxes() {
    addCheckBoxes(innerPanel, editorModel.initialItemsAboveSeparator)
    innerPanel.add(flagDivider)
    addCheckBoxes(innerPanel, editorModel.initialItemsBelowSeparator)
  }

  private fun addCheckBoxes(panel: JPanel, items: List<String>) {
    items.forEach {
      val checkBox = JBCheckBox(it)
      panel.add(checkBox)

      // Add a MouseClick listener instead of an ActionListener such
      // that disabled controls can be changed:
      checkBox.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) {
          editorModel.toggle(checkBox.text)
        }
      })
    }
  }

  private fun addApplyButton() {
    val apply = JButton()
    apply.text = "Apply"
    apply.isDefaultCapable = true
    val minSize = apply.minimumSize
    apply.preferredSize = Dimension(minSize.width * 2, minSize.height)
    apply.addActionListener {
      editorModel.applyChanges()
      balloon?.hide()
    }
    val applyPanel = AdtSecondaryPanel()
    applyPanel.add(apply)
    applyPanel.border = JBUI.Borders.empty(0, 5, 5, 5)
    add(applyPanel, VerticalLayout.BOTTOM)
  }

  private fun handleValueChanged() {
    for (index in 0 until innerPanel.componentCount) {
      val component = innerPanel.getComponent(index) as? JBCheckBox ?: continue
      component.isSelected = editorModel.isSelected(component.text)
      component.isEnabled = editorModel.isEnabled(component.text)
      component.isVisible = editorModel.isVisible(component.text)
    }
    flagDivider.isVisible = editorModel.flagDividerVisible
  }
}
