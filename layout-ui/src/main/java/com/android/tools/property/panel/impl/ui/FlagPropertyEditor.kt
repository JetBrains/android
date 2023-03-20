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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.impl.model.FlagPropertyEditorModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BalloonImpl
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.DefaultFocusTraversalPolicy
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTable
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import kotlin.math.max

private const val MIN_SCROLL_PANE_HEIGHT = 200
private const val WINDOW_MARGIN = 40

/**
 * Editor for a flags property.
 *
 * Displays the value as text with a flag icon on the right.
 * Clicking the flag will bring up a balloon control where the individual flags
 * can be changed.
 */
class FlagPropertyEditor(val editorModel: FlagPropertyEditorModel) : PropertyTextFieldWithLeftButton(editorModel) {

  override fun requestFocus() {
    leftButton?.requestFocus()
  }

  override val buttonAction = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val restoreFocusTo: JComponent = tableParent ?: leftButton!!
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
      balloon.show(RelativePoint.getCenterOf(leftComponent), Balloon.Position.below)
      balloon.setHideListener { panel.hideBalloonAndRestoreFocusOnEditor() }
      ApplicationManager.getApplication().invokeLater { panel.searchField.requestFocus() }
    }
  }

  /**
   * Return the table this [FlagPropertyEditor] is a cell editor for (if any).
   */
  @VisibleForTesting
  val tableParent: JTable?
    get() = SwingUtilities.getAncestorOfClass(JTable::class.java, this) as? JTable

  private val windowHeight: Int
    get() = SwingUtilities.getWindowAncestor(this).height
}

/**
 * A panel to be displayed in a balloon control.
 */
class FlagPropertyPanel(private val editorModel: FlagPropertyEditorModel,
                        private val restoreFocusTo: JComponent,
                        windowHeight: Int) : AdtSecondaryPanel(VerticalLayout(2)) {
  var balloon: Balloon? = null
  val searchField = SearchTextField()
  private val innerPanel = AdtSecondaryPanel(VerticalLayout(2))
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
    val font = StartupUiUtil.labelFont
    val smallerFont = font.deriveFont(font.size2D * 0.9f)
    val selectAll = HyperlinkLabel("Select all")
    selectAll.font = smallerFont
    selectAll.addHyperlinkListener { editorModel.selectAll() }
    val clearAll = HyperlinkLabel("Clear")
    clearAll.font = smallerFont
    clearAll.addHyperlinkListener {
      editorModel.clearAll()
      searchField.text = ""
    }
    val links = AdtSecondaryPanel(BorderLayout(JBUI.scale(5), 0))
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
    scrollPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent?) {
        // unitIncrement affects the scroll wheel speed
        scrollPane.verticalScrollBar.unitIncrement = scrollPane.height

        // blockIncrement affects the page down speed, when clicking above/under the scroll thumb
        scrollPane.verticalScrollBar.blockIncrement = scrollPane.height
      }
    })
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
      checkBox.background = secondaryPanelBackground
      checkBox.addActionListener { editorModel.toggle(checkBox.text) }
    }
  }

  private fun addApplyButton() {
    val apply = JButton()
    apply.text = "Apply"
    apply.isDefaultCapable = true
    apply.background = secondaryPanelBackground
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
