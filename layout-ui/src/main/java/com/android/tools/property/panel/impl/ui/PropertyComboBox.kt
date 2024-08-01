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

import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextBorder
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.HIDE_RIGHT_BORDER
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.impl.model.ComboBoxPropertyEditorModel
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.android.tools.property.panel.impl.support.TextEditorFocusListener
import com.android.tools.property.ptable.KEY_IS_VISUALLY_RESTRICTED
import com.intellij.ide.actions.UndoRedoAction
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.ListCellRenderer
import javax.swing.UIManager
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.UIResource

private const val RIGHT_OVERLAY_MARGIN = 6

/**
 * A standard control for editing property values with a popup list.
 *
 * This control will act as a ComboBox or a DropDown depending on the model.
 *
 * When this control is used as a table cell renderer that is currently expanded, we will show a
 * [PropertyLabel] instead of the ComboBox (or DropDown) since the user will not be able to click on
 * the DropDown button in the popup (the popup will close when the cursor is moved outside of the
 * table).
 */
class PropertyComboBox(
  private val model: ComboBoxPropertyEditorModel,
  private val context: EditorContext,
) : JPanel(BorderLayout()) {
  private val comboBox = WrappedComboBox(model, context)
  private val label = PropertyLabel(model)
  private var initialized = false

  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    comboBox.actionOnKeyNavigation = false
    label.border = JBUI.Borders.emptyRight(RIGHT_OVERLAY_MARGIN)
    add(comboBox)
    add(label)
    model.addListener { updateFromModel() }
    initialized = true
    updateUI()
    updateFromModel()
  }

  var renderer: ListCellRenderer<in EnumValue>
    get() = comboBox.renderer
    set(value) {
      comboBox.renderer = value
    }

  val editor: CommonTextField<*>
    get() = comboBox.editor.editorComponent as CommonTextField<*>

  override fun updateUI() {
    super.updateUI()
    if (initialized) {
      // Editors in a table should have the border here (to be shared by the label and the
      // ComboBox).
      // Standalone editor should have the border on the ComboBox (then it becomes rounded).
      if (context != EditorContext.STAND_ALONE_EDITOR && (border == null || border is UIResource)) {
        border = CommonTextBorder()
      }
    }
  }

  // Override the preferred width of this JPanel to match the component being shown.
  // Without this override, the control will show up with a very low height: b/284255907
  override fun getPreferredSize(): Dimension =
    if (comboBox.isVisible) comboBox.preferredSize.apply { JBInsets.addTo(this, insets) }
    else label.preferredSize.apply { JBInsets.addTo(this, insets) }

  override fun doLayout() {
    // The ComboBox should fill up the entire area of this component.
    comboBox.bounds = Rectangle(0, 0, width, height).apply { JBInsets.removeFrom(this, insets) }

    // The label should be placed indented from the left edge to match the ComboBox.
    val labelBounds =
      comboBox.bounds.apply { JBInsets.removeFrom(this, UIManager.getInsets("ComboBox.padding")) }
    labelBounds.width =
      when (model.tableExpansionState) {
        // The label for the left part of a popup should go to the right edge of the table.
        TableExpansionState.EXPANDED_CELL_FOR_POPUP -> this.width - labelBounds.x
        // The label width of the popup should hold the entire text. The handler will only use the
        // part beyond the right edge of the table.
        TableExpansionState.EXPANDED_POPUP -> label.preferredSize.width
        // If the label fits: use the computation from above.
        else -> labelBounds.width
      }
    label.bounds = labelBounds
  }

  private fun updateFromModel() {
    // Choose either the ComboBox or the PropertyLabel based on the expansion state:
    val editing = model.tableExpansionState == TableExpansionState.NORMAL
    comboBox.isVisible = editing
    label.isVisible = !editing
    // Avoid painting the right vertical edge of the cell border if this is the left part of the
    // complete value:
    ClientProperty.put(
      this,
      HIDE_RIGHT_BORDER,
      model.tableExpansionState == TableExpansionState.EXPANDED_CELL_FOR_POPUP,
    )
  }
}

private class WrappedComboBox(model: ComboBoxPropertyEditorModel, context: EditorContext) :
  CommonComboBox<EnumValue, ComboBoxPropertyEditorModel>(model), UiDataProvider {
  private val textField = editor.editorComponent as CommonTextField<*>
  private var inSetup = false

  init {
    putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)

    // Register key stroke navigation for dropdowns (textField is not editable)
    unregisterKeyboardAction(KeyStrokes.ESCAPE) // Remove existing bindings
    registerActionKey({ enterInPopup() }, KeyStrokes.ENTER, "enter")
    registerActionKey({ enterInPopup() }, KeyStrokes.SPACE, "enter")
    registerActionKey({ escape() }, KeyStrokes.ESCAPE, "escape", { wouldConsumeEscape() })
    registerActionKey(
      { tab { enterInPopup() } },
      KeyStrokes.TAB,
      "tab",
      condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
    )
    registerActionKey({ backtab { enterInPopup() } }, KeyStrokes.BACKTAB, "backtab")
    focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    HelpSupportBinding.registerHelpKeyActions(
      this,
      { model.property },
      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
    )
    if (context != EditorContext.STAND_ALONE_EDITOR) {
      putClientProperty("JComboBox.isTableCellEditor", true)
      // A table cell renderer and editor has a border on the parent PropertyComboBox. Remove the
      // border from the ComboBox itself:
      border = JBUI.Borders.empty()
    }

    // Register key stroke navigation for dropdowns (textField is editable)
    textField.registerActionKey({ enter() }, KeyStrokes.ENTER, "enter")
    textField.registerActionKey({ space() }, KeyStrokes.TYPED_SPACE, "space")
    textField.registerActionKey({ escape() }, KeyStrokes.ESCAPE, "escape", { wouldConsumeEscape() })
    textField.registerActionKey({ tab { enter() } }, KeyStrokes.TAB, "tab")
    textField.registerActionKey({ backtab { enter() } }, KeyStrokes.BACKTAB, "backtab")
    textField.focusTraversalKeysEnabled = false // handle tab and shift-tab ourselves
    textField.background = UIUtil.TRANSPARENT_COLOR
    textField.isOpaque = false
    // For table cell renderers that are not currently expanded: avoid unwanted horizontal
    // scrolling:
    textField.enableScrollInView = context != EditorContext.TABLE_RENDERER
    textField.putClientProperty(UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, true)
    textField.putClientProperty(KEY_IS_VISUALLY_RESTRICTED) {
      // Allow for table expansion when the mouse is hovering over the textField (but not the
      // button):
      model.tableExpansionState != TableExpansionState.NORMAL || width < preferredSize.width
    }

    val focusListener = TextEditorFocusListener(textField, this, model)
    addFocusListener(focusListener)
    textField.addFocusListener(focusListener)
    setFromModel()

    // This action is fired when changes to the selectedIndex is made, which includes mouse clicks
    // and certain keystrokes
    addActionListener {
      if (!inSetup) {
        model.selectEnumValue { newText -> textField.text = newText }
      }
    }

    addPopupMenuListener(
      object : PopupMenuListener {
        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) =
          model.popupMenuWillBecomeInvisible()

        override fun popupMenuCanceled(event: PopupMenuEvent) {}

        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
          model.popupMenuWillBecomeVisible updatePopup@{
            // This callback means the model has an update for the list in the popup.
            //
            // At this point the List in the popup has already resized to the new elements
            // in the popup, but the popup itself must be resized somehow.
            // Do this by calling JPopupMenu.show(Component,x,y) which could be overridden
            // in a LAF implementation of PopupMenuUI.
            val popupMenu = popup as? JPopupMenu ?: return@updatePopup
            if (!isPopupVisible) {
              return@updatePopup
            }
            val location = popupMenu.locationOnScreen
            val comboLocation = this@WrappedComboBox.locationOnScreen
            location.translate(-comboLocation.x, -comboLocation.y)
            popupMenu.show(this@WrappedComboBox, location.x, location.y)
            popupMenu.pack()
          }
        }
      }
    )
  }

  private fun enterInPopup() {
    // This will cause the firing of an action event:
    popup?.list?.selectedIndex?.let { selectedIndex = it }
    hidePopup()
    textField.selectAll()
  }

  private fun enter() {
    if (isPopupVisible) {
      enterInPopup()
      return
    }
    textField.enterInLookup()
    model.enterKeyPressed()
    textField.selectAll()
  }

  private fun wouldConsumeEscape(): Boolean {
    return model.hasPendingChange() || isPopupVisible || textField.isLookupEnabled()
  }

  private fun escape() {
    if (!textField.escapeInLookup()) {
      // Lookup panel did not consume Escape key, trigger key press in model
      model.escapeKeyPressed()
    }
  }

  // Override the handling of the space key for the text editor.
  // If the popup is visible we want to commit the selected value in the popup list,
  // if the popup is not visible emulate normal typing in the text editor.
  private fun space() {
    if (isPopupVisible) {
      enterInPopup()
      return
    }
    textField.replaceSelection(" ")
  }

  private fun tab(action: () -> Unit) {
    action()
    if (isEditable) textField.transferFocus() else transferFocus()
  }

  private fun backtab(action: () -> Unit) {
    action()
    transferFocusBackward()
  }

  override fun updateFromModel() {
    super.updateFromModel()
    setFromModel()
  }

  private fun setFromModel() {
    isVisible = model.visible
    foreground = model.displayedForeground(UIUtil.getLabelForeground())
    background = model.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = model.isUsedInRendererWithSelection
    if (model.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
    if (model.isPopupVisible != isPopupVisible) {
      isPopupVisible = model.isPopupVisible
    }
    if (!model.editable) {
      inSetup = true
      try {
        val currentIndex = model.getIndexOfCurrentValue()
        selectedIndex = currentIndex
        if (currentIndex < 0) {
          model.updateValueFromProperty()
        }
      } finally {
        inSetup = false
      }
    }
  }

  override fun setForeground(color: Color?) {
    super.setForeground(color)

    // This method may be called in constructor of super class. Don't use textField here:
    editor?.editorComponent?.foreground = color
  }

  override fun setBackground(color: Color?) {
    super.setBackground(color)

    // This method may be called in constructor of super class. Don't use textField here:
    editor?.editorComponent?.background = color
  }

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See
    // TableEditor.getToolTip().
    val component = event.source as? JComponent ?: textField
    PropertyTooltip.setToolTip(component, model.property, forValue = true, text = textField.text)
    return null
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[HelpSupport.PROPERTY_ITEM] = model.property
  }

  // Hack: This method is called to update the text editor with the content of the
  // selected item in the dropdown. We do not want that, since the editor text can
  // be different from any of the values in the dropdown. Instead control the text
  // editor value with the [CommonTextFieldModel] part of the comboBox model.
  override fun configureEditor(anEditor: ComboBoxEditor, anItem: Any?) = Unit

  // Hack: JavaDoc specifies that this method should not be overridden.
  // However this causes a value that is not represented in the popup to be
  // overwritten when the control looses focus. This is a workaround for now.
  override fun actionPerformed(event: ActionEvent) = Unit
}
