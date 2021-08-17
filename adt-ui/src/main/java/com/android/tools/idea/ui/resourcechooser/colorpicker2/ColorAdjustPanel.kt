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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import java.awt.AWTPermission
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFERRED_HEIGHT = 70

private const val COLOR_INDICATOR_SIZE = 45
private const val COLOR_INDICATOR_BORDER_SIZE = 6

class ColorAdjustPanel(private val model: ColorPickerModel,
                       private val pipetteProvider: ColorPipetteProvider)
  : JPanel(GridBagLayout()), ColorPickerListener {

  private val pipetteButton by lazy {
    val colorPipetteButton = ColorPipetteButton(model, pipetteProvider.createPipette(this@ColorAdjustPanel))
    with (colorPipetteButton) {
      border = JBUI.Borders.empty()
      background = PICKER_BACKGROUND_COLOR

      setUI(BasicButtonUI())

      isFocusable = false
      preferredSize = JBUI.size(COLOR_INDICATOR_SIZE)
    }
    colorPipetteButton
  }

  @VisibleForTesting
  val colorIndicator = ColorIndicator().apply {
    border = JBUI.Borders.empty(COLOR_INDICATOR_BORDER_SIZE)
    preferredSize = JBUI.size(COLOR_INDICATOR_SIZE)
  }

  @VisibleForTesting
  val hueSlider = HueSliderComponent().apply {
    border = JBUI.Borders.empty(0, 16, 8, 16)
    background = PICKER_BACKGROUND_COLOR

    addListener { it, commit ->
      val hue = it / 360f
      val hsbValues = Color.RGBtoHSB(model.color.red, model.color.green, model.color.blue, null)
      val rgb = Color.HSBtoRGB(hue, hsbValues[1], hsbValues[2])
      val argb = (model.color.alpha shl 24) or (rgb and 0x00FFFFFF)
      val newColor = Color(argb, true)
      (if (commit) model::setColor else model::setPickingColor).invoke(newColor, this)
    }
  }

  @VisibleForTesting
  val alphaSlider = AlphaSliderComponent().apply {
    border = JBUI.Borders.empty(8, 16, 0, 16)
    background = PICKER_BACKGROUND_COLOR

    addListener { it, commit ->
      val newColor = Color(model.color.red, model.color.green, model.color.blue, it)
      (if (commit) model::setColor else model::setPickingColor).invoke(newColor, this)
    }
  }

  init {
    border = JBUI.Borders.empty(0, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)
    background = PICKER_BACKGROUND_COLOR
    preferredSize = JBUI.size(COLOR_PICKER_WIDTH, PREFERRED_HEIGHT)

    // TODO: replace GridBag with other layout.
    val c = GridBagConstraints()

    if (canPickupColorFromDisplay()) {
      c.gridx = 0
      c.gridy = 0
      c.weightx = 0.12
      add(pipetteButton, c)
    }

    c.gridx = 1
    c.gridy = 0
    c.weightx = 0.12
    add(colorIndicator, c)

    c.fill = GridBagConstraints.BOTH
    c.gridx = 2
    c.gridy = 0
    c.weightx = 0.76
    val sliderPanel = JPanel()
    sliderPanel.layout = BoxLayout(sliderPanel, BoxLayout.Y_AXIS)
    sliderPanel.border = JBUI.Borders.empty()
    sliderPanel.background = PICKER_BACKGROUND_COLOR
    sliderPanel.add(hueSlider)
    sliderPanel.add(alphaSlider)
    add(sliderPanel, c)

    model.addListener(this)
  }

  override fun colorChanged(color: Color, source: Any?) {
    if (colorIndicator.color != color) {
      colorIndicator.color = color
    }

    if (source != alphaSlider) {
      // Update alpha slider.
      alphaSlider.sliderBackgroundColor = color
      if (alphaSlider.value != color.alpha) {
        alphaSlider.value = color.alpha
      }
    }

    if (source != alphaSlider && source != hueSlider) {
      // Update hue slider.
      val hue = Color.RGBtoHSB(color.red, color.green, color.blue, null)[0]
      val hueDegree = (hue * 360).roundToInt()
      // Don't change hueSlider.value when (hueSlider.value, hueDegree) is (0, 360) or (360, 0).
      if (abs(hueSlider.value - hueDegree) != 360) {
        hueSlider.value = hueDegree
      }
    }

    repaint()
  }

  override fun pickingColorChanged(color: Color, source: Any?) = colorChanged(color, source)
}

private fun canPickupColorFromDisplay(): Boolean {
  val alphaModeSupported = WindowManager.getInstance()?.isAlphaModeSupported ?: false
  if (!alphaModeSupported) {
    return false
  }

  return try {
    System.getSecurityManager()?.checkPermission(AWTPermission("readDisplayPixels"))
    true
  }
  catch (e: SecurityException) {
    false
  }
}
