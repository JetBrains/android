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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.StatusText
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicComboBoxEditor

/**
 * An abstract surface to render a formatted text to.
 */
interface TextRenderer {
  fun append(text: String, attributes: SimpleTextAttributes)
}

/**
 * A base for drop-down editors providing a selection of pre-formatted text values.
 */
abstract class RenderedComboBox<T>(
  private val itemsModel: DefaultComboBoxModel<T>
) : ComboBox<T>() {

  interface Extension : ExtendableTextComponent.Extension

  protected var lastValueSet: T? = null
    private set
  protected var beingLoaded = false
    private set

  /**
   *  Parses [text] and converts it to the value of type [T] if possible, otherwise returns null.
   */
  abstract fun parseEditorText(text: String): T?

  /**
   * Converts [anObject] to the text format used by this editor for manual input.
   */
  abstract fun toEditorText(anObject: T?): String

  /**
   * Renders [value] the desired rich presentation of the [value] to the receiver renderer.
   */
  abstract fun TextRenderer.renderCell(value: T?)

  // Make the methods callable from the constructor.
  final override fun setRenderer(renderer: ListCellRenderer<in T>?) = super.setRenderer(renderer)

  /**
   * Sets the current value of the editor and makes the inactive editor renderer the presentation of [value].
   *
   * Note: It might be necessary to call setValue() in response to selectedItemChanged if the value returned by [parseEditorText] needs
   *       to be further enriched to render the proper presentation.
   */
  fun setValue(value: T) {
    beingLoaded = true
    try {
      lastValueSet = value
      selectedItem = value
    }
    finally {
      beingLoaded = false
    }
  }

  override fun selectedItemChanged() {
    @Suppress("UNCHECKED_CAST")
    val value = selectedItem as T?
    super.selectedItemChanged()
    val jbTextField = comboBoxEditor.editorComponent
    val emptyText = jbTextField.emptyText
    emptyText.clear()
    jbTextField.setTextToTriggerEmptyTextStatus(toEditorText(value))
    emptyText.toRenderer().renderCell(value)
  }

  /**
   * Populates the drop-down list of the combo-box.
   *
   * Note: The exact presentation of items is determined by [TextRenderer.renderCell].
   */
  fun setKnownValues(knownValues: List<T>) {
    beingLoaded = true
    try {
      val prevItemCount = itemsModel.size
      val selectedItem = itemsModel.selectedItem
      itemsModel.removeAllElements()
      knownValues.forEach { itemsModel.addElement(it) }
      if (isPopupVisible &&prevItemCount == 0) {
        hidePopup()
        showPopup()
      }
      itemsModel.selectedItem = selectedItem
    }
    finally {
      beingLoaded = false
    }
  }

  private val comboBoxEditor = object : BasicComboBoxEditor() {
    override fun setItem(anObject: Any?) {
      @Suppress("UNCHECKED_CAST")
      editor.text = toEditorText(anObject as T?)
    }

    override fun getItem(): Any? = parseEditorText(editor.text.trim())


    override fun createEditorComponent(): JTextField {
      val field =
        object : ExtendableTextField() {
          var ignoreBorderChange: Boolean = false

          init {
            setExtensions(createEditorExtensions())
          }

          override fun setBorder(border: Border?) {
            // ComboBox sets empty borders and we need to reserve space for icons. If [ExtendableTextField] is used as a standalone
            // component it creates DarculaTextBorder which which similarly reserves the required space.
            // We do not check whether [border] is [DarculaTextBorder] here to avoid not necessary dependencies.
            ignoreBorderChange = true
            try {
              super.setBorder(border?.adjustBorder(this))
            }
            finally {
              ignoreBorderChange = false
            }
          }

          override fun firePropertyChange(propertyName: String?, oldValue: Any?, newValue: Any?) {
            // Prevent a StackOverflow caused by both this class and MacIntelliJComboBoxUI hooking setBorder in the same way.
            if (ignoreBorderChange && propertyName == "border") return
            super.firePropertyChange(propertyName, oldValue, newValue)
          }
        }
      field.addFocusListener(object : FocusListener {
        override fun focusGained(e: FocusEvent) {
          update(e)
        }

        override fun focusLost(e: FocusEvent) {
          update(e)
        }

        private fun update(e: FocusEvent) {
          val c = e.component.parent
          if (c != null) {
            c.revalidate()
            c.repaint()
          }
        }
      })

      return field
    }

    override fun getEditorComponent(): JBTextField = super.getEditorComponent() as JBTextField
  }

  open fun createEditorExtensions(): List<Extension> = listOf()

  private val comboBoxRenderer = object : ColoredListCellRenderer<T>() {
    override fun customizeCellRenderer(list: JList<out T>,
                                       value: T?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      toRenderer().renderCell(value)
    }
  }

  init {
    super.setModel(itemsModel)
    setEditor(comboBoxEditor)
    setRenderer(comboBoxRenderer)
  }
}

internal fun StatusText.toRenderer() = object : TextRenderer {
  override fun append(text: String, attributes: SimpleTextAttributes) {
    this@toRenderer.appendText(text, attributes)
  }
}

internal fun SimpleColoredComponent.toRenderer() = object : TextRenderer {
  override fun append(text: String, attributes: SimpleTextAttributes) {
    this@toRenderer.append(text, attributes)
  }
}

class AdjustedBorder(val border: Border, val component: Component, val icons: Int) : Border {
  override fun isBorderOpaque(): Boolean = border.isBorderOpaque

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) =
    border.paintBorder(c, g, x, y, width, height)

  override fun getBorderInsets(c: Component?): Insets =
    border.getBorderInsets(c).let {
      Insets(it.top, it.left, it.bottom, it.right + icons)
    }
}

private fun Border.adjustBorder(component: ExtendableTextField) =
  AdjustedBorder(this, component, component.extensions?.sumBy { it.preferredSpace } ?: 0)
