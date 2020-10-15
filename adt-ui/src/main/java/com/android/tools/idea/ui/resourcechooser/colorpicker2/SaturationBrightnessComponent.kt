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

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import javax.swing.JComponent

private val KNOB_COLOR = Color.WHITE

class SaturationBrightnessComponent(private val myModel: ColorPickerModel) : JComponent(), ColorPickerListener {

  private val knobOuterRadius = JBUI.scale(4)
  private val knobInnerRadius = JBUI.scale(3)

  var brightness = 1f
    private set
  var hue = 1f
    private set
  var saturation = 0f
    private set
  var alpha: Int = 0
    private set

  private var knobX: Int = 0
  private var knobY: Int = 0

  init {
    isOpaque = false
    background = Color.WHITE

    val mouseAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        handleMouseEvent(e, false)
      }

      override fun mouseDragged(e: MouseEvent) {
        handleMouseEvent(e, false)
      }

      override fun mouseReleased(e: MouseEvent) {
        handleMouseEvent(e, true)
      }
    }
    addMouseListener(mouseAdapter)
    addMouseMotionListener(mouseAdapter)
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = setKnobPosition(myModel.hsb)
    })

    myModel.addListener(this)
  }

  private fun handleMouseEvent(e: MouseEvent, commit: Boolean) {
    knobX = Math.max(0, Math.min(e.x, size.width))
    knobY = Math.max(0, Math.min(e.y, size.height))

    val saturation = knobX.toFloat() / size.width
    val brightness = 1.0f - knobY.toFloat() / size.height

    val argb = ahsbToArgb(alpha, hue, saturation, brightness)
    (if (commit) myModel::setColor else myModel::setPickingColor).invoke(Color(argb, true), this)
    repaint()
  }

  override fun getPreferredSize(): Dimension = JBUI.size(COLOR_PICKER_WIDTH, 150)

  override fun getMinimumSize(): Dimension = JBUI.size(150, 140)

  override fun paintComponent(g: Graphics) {
    val image = createImage(SaturationBrightnessImageProducer(size.width, size.height, hue))
    g.color = UIUtil.getPanelBackground()
    g.fillRect(0, 0, width, height)
    g.drawImage(image, 0, 0, null)

    g.color = KNOB_COLOR
    g.drawOval(knobX - knobOuterRadius,
               knobY - knobOuterRadius,
               knobOuterRadius * 2,
               knobOuterRadius * 2)
    g.drawOval(knobX - knobInnerRadius,
               knobY - knobInnerRadius,
               knobInnerRadius * 2,
               knobInnerRadius * 2)
  }

  override fun colorChanged(color: Color, source: Any?) {
    if (source == this) {
      return
    }
    val hsbValues = Color.RGBtoHSB(color.red, color.green, color.blue, null)
    setHSBAValue(hsbValues[0], hsbValues[1], hsbValues[2], color.alpha)
    setKnobPosition(hsbValues)
    repaint()
  }

  private fun setHSBAValue(h: Float, s: Float, b: Float, a: Int) {
    hue = h
    saturation = s
    brightness = b
    alpha = a
  }

  private fun setKnobPosition(hsbArray: FloatArray) {
    knobX = (hsbArray[1] * size.width).toInt()
    knobY = ((1f - hsbArray[2]) * size.height).toInt()
  }

  override fun pickingColorChanged(color: Color, source: Any?) = colorChanged(color, source)
}

private class SaturationBrightnessImageProducer(imageWidth: Int, imageHeight: Int, hue: Float)
  : MemoryImageSource(imageWidth, imageHeight, null, 0, imageWidth) {

  init {
    val saturation = FloatArray(imageWidth * imageHeight)
    val brightness = FloatArray(imageWidth * imageHeight)

    // create lookup tables
    for (x in 0 until imageWidth) {
      for (y in 0 until imageHeight) {
        val index = x + y * imageWidth
        saturation[index] = x.toFloat() / imageWidth
        brightness[index] = 1.0f - y.toFloat() / imageHeight
      }
    }

    val pixels = IntArray(imageWidth * imageHeight)
    newPixels(pixels, ColorModel.getRGBdefault(), 0, imageWidth)
    setAnimated(true)
    for (index in pixels.indices) {
      pixels[index] = Color.HSBtoRGB(hue, saturation[index], brightness[index])
    }
    newPixels()
  }
}
