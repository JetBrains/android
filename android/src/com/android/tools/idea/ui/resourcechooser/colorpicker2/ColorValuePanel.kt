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

import com.android.annotations.VisibleForTesting
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AttributeSet
import javax.swing.text.PlainDocument
import kotlin.math.roundToInt

private val PANEL_BORDER = JBUI.Borders.empty(0, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)

private val PREFERRED_PANEL_SIZE = JBUI.size(PICKER_PREFERRED_WIDTH, 50)

private const val TEXT_FIELDS_UPDATING_DELAY = 300

private val COLOR_RANGE = 0..255
private val HUE_RANGE = 0..360
private val PERCENT_RANGE = 0..100

private const val BORDER_CORNER_ARC = 7

private const val HOVER_BORDER_LEFT = 0
private const val HOVER_BORDER_TOP = 0
private const val HOVER_BORDER_WIDTH = 1
private val HOVER_BORDER_STROKE = BasicStroke(1f)
private val HOVER_BORDER_COLOR = Color.GRAY.brighter()

private const val PRESSED_BORDER_LEFT = 1
private const val PRESSED_BORDER_TOP = 1
private const val PRESSED_BORDER_WIDTH = 2
private val PRESSED_BORDER_STROKE = BasicStroke(1.2f)
private val PRESSED_BORDER_COLOR = Color.GRAY

class ColorValuePanel(private val model: ColorPickerModel)
  : JPanel(GridBagLayout()), DocumentListener, ColorListener {

  enum class ColorFormat {
    RGB,
    HSB;

    fun next() : ColorFormat = when (this) {
      RGB -> HSB
      HSB -> RGB
    }
  }

  /**
   * Used to update the color of picker when color text fields are edited.
   */
  @get:TestOnly
  val updateAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)

  @get:TestOnly
  val alphaField = ColorValueField()
  @get:TestOnly
  val hexField = ColorValueField(hex = true)

  private val colorLabel1 = ColorLabel()
  private val colorLabel2 = ColorLabel()
  private val colorLabel3 = ColorLabel()

  @get:TestOnly
  val colorField1 = ColorValueField()
  private val redDocument = DigitColorDocument(colorField1, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val hueDocument = DigitColorDocument(colorField1, HUE_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  @get:TestOnly
  val colorField2 = ColorValueField()
  private val greenDocument = DigitColorDocument(colorField2, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val saturationDocument = DigitColorDocument(colorField2, PERCENT_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  @get:TestOnly
  val colorField3 = ColorValueField()
  private val blueDocument = DigitColorDocument(colorField3, COLOR_RANGE).apply { addDocumentListener(this@ColorValuePanel) }
  private val brightnessDocument = DigitColorDocument(colorField3, PERCENT_RANGE).apply { addDocumentListener(this@ColorValuePanel) }

  @VisibleForTesting
  var currentColorFormat = ColorFormat.RGB
    set(value) {
      field = value
      updateColorFormat()
      repaint()
    }

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
    add(alphaField, c)
    alphaField.document = DigitColorDocument(alphaField, COLOR_RANGE)
    alphaField.document.addDocumentListener(this)

    c.weightx = 0.36
    c.gridwidth = 3
    c.gridx = 1
    c.gridy = 0
    add(createFormatLabels(), c)

    c.gridwidth = 1
    c.weightx = 0.12
    c.gridx = 1
    c.gridy = 1
    add(colorField1, c)
    c.gridx = 2
    c.gridy = 1
    add(colorField2, c)
    c.gridx = 3
    c.gridy = 1
    add(colorField3, c)

    // Hex should be longer
    c.gridheight = 1
    c.weightx = 0.51
    c.gridx = 4
    c.gridy = 0
    add(ColorLabel("Hex"), c)
    c.gridy = 1
    add(hexField, c)
    hexField.document = HexColorDocument(hexField)
    hexField.document.addDocumentListener(this)

    updateColorFormat()

    model.addListener(this)
  }

  private fun createFormatLabels() = ColorFormatLabels()

  private enum class MouseStatus { NORMAL, HOVER, PRESSED }

  private inner class ColorFormatLabels : JPanel(GridLayout(1, 3)) {

    private var mouseStatus = MouseStatus.NORMAL
      set(value) {
        field = value
        repaint()
      }

    init {
      border = BorderFactory.createEmptyBorder()
      background = PICKER_BACKGROUND_COLOR
      add(colorLabel1)
      add(colorLabel2)
      add(colorLabel3)

      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          currentColorFormat = currentColorFormat.next()
        }

        override fun mouseEntered(e: MouseEvent?) {
          mouseStatus = MouseStatus.HOVER
        }

        override fun mouseExited(e: MouseEvent?) {
          mouseStatus = MouseStatus.NORMAL
        }

        override fun mousePressed(e: MouseEvent?) {
          mouseStatus = MouseStatus.PRESSED
        }

        override fun mouseReleased(e: MouseEvent?) {
          mouseStatus = if (mouseStatus == MouseStatus.PRESSED) MouseStatus.HOVER else MouseStatus.NORMAL
        }
      })
    }

    override fun paintBorder(g: Graphics) {
      g as? Graphics2D ?: return
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val originalStroke = g.stroke
      when (mouseStatus) {
        MouseStatus.HOVER -> {
          g.stroke = HOVER_BORDER_STROKE
          g.color = HOVER_BORDER_COLOR
          g.drawRoundRect(HOVER_BORDER_LEFT,HOVER_BORDER_TOP,
                          width - HOVER_BORDER_WIDTH, height - HOVER_BORDER_WIDTH,
                          BORDER_CORNER_ARC, BORDER_CORNER_ARC)
        }
        MouseStatus.PRESSED -> {
          g.stroke = PRESSED_BORDER_STROKE
          g.color = PRESSED_BORDER_COLOR
          g.drawRoundRect(PRESSED_BORDER_LEFT, PRESSED_BORDER_TOP,
                          width - PRESSED_BORDER_WIDTH, height - PRESSED_BORDER_WIDTH,
                          BORDER_CORNER_ARC, BORDER_CORNER_ARC)
        }
        else -> return
      }
      g.stroke = originalStroke
    }
  }

  private fun updateColorFormat() {
    when (currentColorFormat) {
      ColorFormat.RGB -> {
        colorLabel1.text = "R"
        colorLabel2.text = "G"
        colorLabel3.text = "B"

        colorField1.document = redDocument
        colorField2.document = greenDocument
        colorField3.document = blueDocument

        colorField1.text = model.red.toString()
        colorField2.text = model.green.toString()
        colorField3.text = model.blue.toString()

        // Add listener after setting value, so the document listener won't be triggered.
      }
      ColorFormat.HSB -> {
        colorLabel1.text = "H"
        colorLabel2.text = "S"
        colorLabel3.text = "B"

        colorField1.document = hueDocument
        colorField2.document = saturationDocument
        colorField3.document = brightnessDocument

        colorField1.text = (model.hue * 360).roundToInt().toString()
        colorField2.text = (model.saturation * 100).roundToInt().toString()
        colorField3.text = (model.brightness * 100).roundToInt().toString()
      }
    }
    // change the text in document trigger the listener, but it doesn't to update the color in Model in this case.
    updateAlarm.cancelAllRequests()
    repaint()
  }

  override fun colorChanged(color: Color, source: Any?) = updateTextField(color, source)

  private fun updateTextField(color: Color, source: Any?) {
    alphaField.setTextIfNeeded(color.alpha.toString(), source)
    if (currentColorFormat == ColorFormat.RGB) {
      colorField1.setTextIfNeeded(color.red.toString(), source)
      colorField2.setTextIfNeeded(color.green.toString(), source)
      colorField3.setTextIfNeeded(color.blue.toString(), source)
    }
    else {
      val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
      colorField1.setTextIfNeeded((hsb[0] * 360).roundToInt().toString(), source)
      colorField2.setTextIfNeeded((hsb[1] * 100).roundToInt().toString(), source)
      colorField3.setTextIfNeeded((hsb[2] * 100).roundToInt().toString(), source)
    }
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
    val color = when {
      src == hexField -> convertHexToColor(hexField.text)
      currentColorFormat == ColorFormat.RGB -> {
        val a = alphaField.colorValue
        val r = colorField1.colorValue
        val g = colorField2.colorValue
        val b = colorField3.colorValue
        Color(r, g, b, a)
      }
      currentColorFormat == ColorFormat.HSB -> {
        val a = alphaField.colorValue
        val h = colorField1.colorValue / 360f
        val s = colorField2.colorValue / 100f
        val b = colorField3.colorValue / 100f
        Color((a shl 24) or (0x00FFFFFF and Color.HSBtoRGB(h, s, b)), true)
      }
      else -> return
    }

    model.setColor(color, this)
  }
}

private class ColorLabel(text: String = ""): JLabel(text, SwingConstants.CENTER) {
  init {
    foreground = PICKER_TEXT_COLOR
  }
}

class ColorValueField(private val hex: Boolean = false): JTextField(if (hex) 8 else 3) {

  init {
    horizontalAlignment = JTextField.CENTER
    isEnabled = true
    isEditable = true

    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        selectAll()
      }
    })
  }

  val colorValue: Int
    get() {
      val rawText = text
      return if (rawText.isBlank()) 0 else Integer.parseInt(rawText, if (hex) 16 else 10)
    }
}

private abstract class ColorDocument(internal val src: JTextField) : PlainDocument() {

  override fun insertString(offs: Int, str: String, a: AttributeSet?) {
    val source = str.toCharArray()
    val selected = src.selectionEnd - src.selectionStart
    val newLen = src.text.length - selected + str.length
    if (newLen > src.columns) {
      return
    }

    val charsToInsert = source
      .filter { isLegalCharacter(it) }
      .map { it.toUpperCase() }
      .joinToString("")

    val res = StringBuilder(src.text).insert(offs, charsToInsert).toString()
    if (!isLegalValue(res)) {
      return
    }
    super.insertString(offs, charsToInsert, a)
  }

  abstract fun isLegalCharacter(c: Char): Boolean

  abstract fun isLegalValue(str: String): Boolean
}

private class DigitColorDocument(src: JTextField, private val valueRange: IntRange) : ColorDocument(src) {

  override fun isLegalCharacter(c: Char) = c.isDigit()

  override fun isLegalValue(str: String) = try { str.toInt() in valueRange } catch (_: NumberFormatException) { false }
}

private class HexColorDocument(src: JTextField) : ColorDocument(src) {

  override fun isLegalCharacter(c: Char) = StringUtil.isHexDigit(c)

  override fun isLegalValue(str: String) = true
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
