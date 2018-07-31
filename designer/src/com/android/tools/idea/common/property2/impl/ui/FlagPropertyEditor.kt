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

import com.android.annotations.VisibleForTesting
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.stdui.registerKeyAction
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.FlagPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.math.max

private const val MIN_SCROLL_PANE_HEIGHT = 200
private const val WINDOW_MARGIN = 40

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
    button.registerKeyAction({ editorModel.enterKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    button.registerKeyAction({ editorModel.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    button.registerKeyAction({ editorModel.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")
    addFocusListener(EditorFocusListener(editorModel))
    button.addActionListener { buttonPressed() }
    editorModel.addListener(ValueChangedListener { handleValueChanged() })
    handleValueChanged()
  }

  override fun requestFocus() {
    button.requestFocus()
  }

  private fun buttonPressed() {
    val restoreFocusTo: JComponent = tableParent ?: button
    editorModel.buttonPressed()
    val panel = FlagPropertyPanel(editorModel, restoreFocusTo, windowHeight)

    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(panel)
      .setShadow(true)
      .setHideOnAction(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(200)
      .setFillColor(secondaryPanelBackground)
      .createBalloon() as BalloonImpl

    panel.balloon = balloon
    balloon.show(RelativePoint.getCenterOf(button), Balloon.Position.below)
    balloon.setHideListener { panel.hideBalloonAndRestoreFocusOnEditor() }
    ApplicationManager.getApplication().invokeLater { panel.searchField.requestFocus() }
  }

  /**
   * Return the table this [FlagPropertyEditor] is a cell editor for (if any).
   */
  @VisibleForTesting
  val tableParent: JTable?
    get() = parent?.parent as? JTable

  private val windowHeight: Int
    get() = SwingUtilities.getWindowAncestor(this).height

  private fun handleValueChanged() {
    button.text = editorModel.buttonText
    isVisible = editorModel.visible
    toolTipText = editorModel.tooltip
    if (editorModel.focusRequest && !isFocusOwner) {
      button.requestFocusInWindow()
    }
  }
}

/**
 * A panel to be displayed in a balloon control.
 */
class FlagPropertyPanel(private val editorModel: FlagPropertyEditorModel,
                        private val restoreFocusTo: JComponent,
                        windowHeight: Int) : AdtSecondaryPanel(VerticalLayout(JBUI.scale(2))) {
  var balloon: Balloon? = null
  val searchField = SearchTextField()
  private val innerPanel = AdtSecondaryPanel(VerticalLayout(JBUI.scale(2)))
  private val flagDivider = JSeparator()

  init {
    addLinks()
    addSearchField()
    addAllCheckBoxes()
    val scrollPane = add(createScrollPane(innerPanel))
    add(JSeparator())
    addApplyButton()
    handleValueChanged()
    isFocusCycleRoot = true
    focusTraversalPolicy = CustomFocusTraversalPolicy(searchField.textEditor)

    editorModel.addListener(ValueChangedListener { handleValueChanged() })

    // If there are too many controls to fit inside the Application Window, set the preferred height of the scroll pane.
    if (preferredSize.height + 2 * JBUI.scale(WINDOW_MARGIN) > windowHeight) {
      val otherControlsHeight = preferredSize.height - innerPanel.preferredSize.height
      val preferredHeight = windowHeight - 2 * JBUI.scale(WINDOW_MARGIN) - otherControlsHeight
      scrollPane.preferredSize = Dimension(-1, max(preferredHeight, JBUI.scale(MIN_SCROLL_PANE_HEIGHT)))
    }
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
    val scrollPane = ScrollPaneFactory.createScrollPane(
      component,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = JBUI.Borders.empty()
    scrollPane.verticalScrollBar.unitIncrement = VERTICAL_SCROLLING_UNIT_INCREMENT
    scrollPane.verticalScrollBar.blockIncrement = VERTICAL_SCROLLING_BLOCK_INCREMENT
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
      checkBox.addActionListener { editorModel.toggle(checkBox.text) }
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
      hideBalloonAndRestoreFocusOnEditor()
    }
    val applyPanel = AdtSecondaryPanel()
    applyPanel.add(apply)
    applyPanel.border = JBUI.Borders.empty(0, 5, 5, 5)
    add(applyPanel, VerticalLayout.BOTTOM)
  }

  fun hideBalloonAndRestoreFocusOnEditor() {
    balloon?.hide()
    val restoreTo = restoreFocusTo
    if (restoreTo is JTable) {
      // If this is a table editor, the original editor is gone as soon as the focus is lost to the balloon.
      // Ideally we want focus to go back to the editor where the balloon was invoked from.
      // Recreate the editor and request focus on the newly created editor here.
      restoreTo.editCellAt(restoreTo.selectedRow, restoreTo.selectedColumn)
      restoreTo.editorComponent?.requestFocus()
    }
    else {
      restoreTo.requestFocus()
    }
  }

  private fun handleValueChanged() {
    for (index in 0 until innerPanel.componentCount) {
      val component = innerPanel.getComponent(index) as? JBCheckBox ?: continue
      component.isSelected = editorModel.isSelected(component.text)
      component.foreground = getForegroundColor(editorModel.isEnabled(component.text))
      component.isVisible = editorModel.isVisible(component.text)
    }
    flagDivider.isVisible = editorModel.flagDividerVisible
  }

  private fun getForegroundColor(enabled: Boolean): Color? {
    return if (enabled) UIUtil.getLabelTextForeground() else UIUtil.getLabelDisabledForeground()
  }
}

/**
 * A [DefaultFocusTraversalPolicy] which accept the [searchField] as a possible component.
 *
 * The [searchField] has a NullComponentPeer which disables focus transferal
 * in the [DefaultFocusTraversalPolicy].
 */
class CustomFocusTraversalPolicy(private val searchField: JComponent): DefaultFocusTraversalPolicy() {
  override fun accept(component: Component): Boolean {
    return searchField == component || super.accept(component)
  }
}
