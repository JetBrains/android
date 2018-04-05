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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument

private val PANEL_BORDER = JBUI.Borders.empty(0, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)

private val PREFERRED_PANEL_SIZE = JBUI.size(PICKER_PREFERRED_WIDTH, 50)

private const val TEXT_FIELDS_UPDATING_DELAY = 300

class ColorValuePanel(private val model: ColorPickerModel)
  : JPanel(GridBagLayout()), DocumentListener, ColorListener {

  /**
   * Used to update the color of picker when color text fields are edited.
   */
  @get:TestOnly
  val updateAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)

  @get:TestOnly
  val aField: JTextField = ColorValueField()
  @get:TestOnly
  val rField: JTextField = ColorValueField()
  @get:TestOnly
  val gField: JTextField = ColorValueField()
  @get:TestOnly
  val bField: JTextField = ColorValueField()
  @get:TestOnly
  val hexField: JTextField = ColorValueField(isHex = true)

  init {
    border = PANEL_BORDER
    preferredSize = PREFERRED_PANEL_SIZE
    background = PICKER_BACKGROUND_COLOR

    val c = GridBagConstraints()
    c.fill = GridBagConstraints.HORIZONTAL

    c.weightx = 0.12

    c.gridx = 0
    c.gridy = 0
    add(ColorLabel("A"), c)
    c.gridy = 1
    add(aField, c)

    c.gridx = 1
    c.gridy = 0
    add(ColorLabel("R"), c)
    c.gridy = 1
    add(rField, c)

    c.gridx = 2
    c.gridy = 0
    add(ColorLabel("G"), c)
    c.gridy = 1
    add(gField, c)

    c.gridx = 3
    c.gridy = 0
    add(ColorLabel("B"), c)
    c.gridy = 1
    add(bField, c)

    // Hex should be longer
    c.weightx = 0.51
    c.gridx = 4
    c.gridy = 0
    add(ColorLabel("Hex"), c)
    c.gridy = 1
    add(hexField, c)

    setListenersToColorField(aField)
    setListenersToColorField(rField)
    setListenersToColorField(gField)
    setListenersToColorField(bField)
    setListenersToColorField(hexField)

    model.addListener(this)
  }

  private fun setListenersToColorField(field: JTextField) {
    field.document.addDocumentListener(this)
    field.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        field.selectAll()
      }
    })
  }

  override fun colorChanged(color: Color, source: Any?) = updateTextField(color, source)

  private fun updateTextField(color: Color, source: Any?) {
    aField.setTextIfNeeded(color.alpha.toString(), source)
    rField.setTextIfNeeded(color.red.toString(), source)
    gField.setTextIfNeeded(color.green.toString(), source)
    bField.setTextIfNeeded(color.blue.toString(), source)
    hexField.setTextIfNeeded(Integer.toHexString(color.rgb), source)
    // Cleanup the update requests which triggered by setting text in this function
    updateAlarm.cancelAllRequests()
  }

  private fun JTextField.setTextIfNeeded(newText: String?, source: Any?) {
    if (text != newText && (source != this@ColorValuePanel || !isFocusOwner)) {
      text = newText
    }
  }

  override fun insertUpdate(e: DocumentEvent) = update((e.document as ColorDocument).src)

  override fun removeUpdate(e: DocumentEvent) = update((e.document as ColorDocument).src)

  override fun changedUpdate(e: DocumentEvent) = Unit

  private fun update(src: JTextField) {
    updateAlarm.cancelAllRequests()
    updateAlarm.addRequest({ updateColorToColorModel(src) }, TEXT_FIELDS_UPDATING_DELAY)
  }

  private fun updateColorToColorModel(src: JTextField?) {
    val color = if (src == hexField) {
      convertHexToColor(hexField.text)
    }
    else {
      val a = if (aField.text == "") 0 else Integer.parseInt(aField.text)
      val r = if (rField.text == "") 0 else Integer.parseInt(rField.text)
      val g = if (gField.text == "") 0 else Integer.parseInt(gField.text)
      val b = if (bField.text == "") 0 else Integer.parseInt(bField.text)
      Color(r, g, b, a)
    }

    model.setColor(color, this)
  }
}

private class ColorLabel(text: String): JLabel(text, SwingConstants.CENTER) {
  init {
    foreground = TEXT_COLOR
  }
}

private const val HEX_DIGIT_NUMBER = 8
private const val VALUE_DIGIT_NUMBER = 3
private val HEX_DIGIT_PATTERN = Pattern.compile("[0-9a-fA-F]")

private class ColorValueField(isHex: Boolean = false): JTextField(if (isHex) HEX_DIGIT_NUMBER else VALUE_DIGIT_NUMBER) {
  private val doc = ColorDocument(this, isHex)

  init {
    horizontalAlignment = JTextField.CENTER
    isEnabled = true
    isEditable = true
    document = doc
  }
}

private class ColorDocument(internal val src: JTextField, private val isHex: Boolean) : PlainDocument() {

  override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
    val source = str!!.toCharArray()
    val selected = src.selectionEnd - src.selectionStart
    val newLen = src.text.length - selected + str.length
    if (newLen > (if (isHex) HEX_DIGIT_NUMBER else VALUE_DIGIT_NUMBER)) {
      // TODO: consider to add some virtual effects to inform user?
      return
    }

    val charsToInsert = source
      .filter { isLegal(it) }
      .map { it.toUpperCase() }
      .joinToString("")

    val res = StringBuilder(src.text).insert(offs, charsToInsert).toString()

    if (!isHex) {
      try {
        val num = Integer.parseInt(res)
        if (num > 255) {
          // TODO: consider to add some virtual effects to inform user?
          return
        }
      }
      catch (ignore: NumberFormatException) {
        // This should not happened since the input characters must be digits.
      }
    }
    super.insertString(offs, charsToInsert, a)
  }

  private fun isLegal(c: Char): Boolean = if (isHex) StringUtil.isHexDigit(c) else Character.isDigit(c)
}

private fun convertHexToColor(hex: String): Color {
  val s = if (hex == "") "0" else hex
  val i = s.toLong(16)
  val a = if (hex.length > 6) i shr 24 and 0xFF else 0xFF
  val r = i shr 16 and 0xFF
  val g = i shr 8 and 0xFF
  val b = i and 0xFF
  return Color(r.toInt(), g.toInt(), b.toInt(), a.toInt())
}
