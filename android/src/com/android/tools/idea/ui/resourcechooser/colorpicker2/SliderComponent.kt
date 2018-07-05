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
import com.intellij.ui.JBColor
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI

import javax.swing.*
import java.awt.*
import java.awt.event.*
import kotlin.math.max

private val DEFAULT_HORIZONTAL_PADDING = JBUI.scale(5)
private val DEFAULT_VERTICAL_PADDING = JBUI.scale(5)

private val KNOB_COLOR = Color(255, 255, 255)
private val KNOB_BORDER_COLOR = JBColor(Color(100, 100, 100), Color(64, 64, 64))
private val KNOB_BORDER_STROKE = BasicStroke(1.5f)
private const val KNOB_WIDTH = 5

abstract class SliderComponent<T: Number>(initialValue: T) : JComponent() {

  protected val leftPadding = DEFAULT_HORIZONTAL_PADDING
  protected val rightPadding = DEFAULT_HORIZONTAL_PADDING
  protected val topPadding = DEFAULT_VERTICAL_PADDING
  protected val bottomPadding = DEFAULT_VERTICAL_PADDING

  private var _knobPosition: Int = 0
  var knobPosition: Int
    get() = _knobPosition
    @VisibleForTesting
    set(newPointerValue) {
      _knobPosition = newPointerValue
      _value = knobPositionToValue(newPointerValue)
    }

  private var _value: T = initialValue
  var value: T
    get() = _value
    set(newValue) {
      _value = newValue
      _knobPosition = valueToKnobPosition(newValue)
    }

  private val myPolygonToDraw = Polygon()

  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<(T) -> Unit>()

  /**
   * @return size of slider, must be positive value or zero.
   */
  @VisibleForTesting
  val sliderWidth get() = max(0, width - leftPadding - rightPadding)

  init {
    this.addMouseMotionListener(object : MouseAdapter() {
      override fun mouseDragged(e: MouseEvent) {
        processMouse(e)
        e.consume()
      }
    })

    this.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        processMouse(e)
        e.consume()
      }
    })

    addMouseWheelListener { e ->
      val amount = when {
        e.scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL -> e.unitsToScroll * e.scrollAmount
        e.wheelRotation < 0 -> -e.scrollAmount
        else -> e.scrollAmount
      }
      val knobPosition = valueToKnobPosition(value)
      val newKnobPosition = Math.max(0, Math.min(knobPosition + amount, sliderWidth))
      value = knobPositionToValue(newKnobPosition)

      repaint()
      fireValueChanged()
    }

    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        repaint()
      }
    })
  }

  private fun processMouse(e: MouseEvent) {
    val knobPosition = Math.max(0, Math.min(e.x - leftPadding, sliderWidth))
    value = knobPositionToValue(knobPosition)

    repaint()
    fireValueChanged()
  }

  fun addListener(listener: (T) -> Unit) {
    listeners.add(listener)
  }

  private fun fireValueChanged() = listeners.forEach { it.invoke(value) }

  protected abstract fun knobPositionToValue(knobPosition: Int): T

  protected abstract fun valueToKnobPosition(value: T): Int

  override fun getPreferredSize(): Dimension = JBUI.size(100, 22)

  override fun getMinimumSize(): Dimension = JBUI.size(50, 22)

  override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

  override fun setToolTipText(text: String) = Unit

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    paintSlider(g2d)
    drawKnob(g2d, leftPadding + valueToKnobPosition(value))
  }

  protected abstract fun paintSlider(g2d: Graphics2D)

  private fun drawKnob(g2d: Graphics2D, x: Int) {
    val originalAntialiasing = g2d.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val originalStroke = g2d.stroke

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val knobLeft = x - KNOB_WIDTH / 2
    val knobTop = topPadding / 2
    val knobWidth = KNOB_WIDTH
    val knobHeight = height - (topPadding + bottomPadding) / 2
    val knobCornerArc = 5

    g2d.color = KNOB_COLOR
    g2d.fillRoundRect(knobLeft, knobTop, knobWidth, knobHeight, knobCornerArc, knobCornerArc)
    g2d.color = KNOB_BORDER_COLOR
    g2d.stroke = KNOB_BORDER_STROKE
    g2d.drawRoundRect(knobLeft, knobTop, knobWidth, knobHeight, knobCornerArc, knobCornerArc)

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
    g2d.stroke = originalStroke
  }
}
