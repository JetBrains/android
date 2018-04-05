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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI

import javax.swing.*
import java.awt.*
import java.awt.event.*
import kotlin.math.max

private val DEFAULT_HORIZONTAL_MARGIN = JBUI.scale(5)

private val KNOB_COLOR = Color(153, 51, 0)
private val KNOB_SHADOW_COLOR = Color(0, 0, 0, 70)
private const val KNOB_HALF_WIDTH = 6
private const val KNOB_HALF_HEIGHT = 6
private const val KNOB_SHADOW_DISTANCE = 1

abstract class SliderComponent<T: Number>(initialValue: T) : JComponent() {

  protected var myLeftMargin = DEFAULT_HORIZONTAL_MARGIN
  protected var myRightMargin = DEFAULT_HORIZONTAL_MARGIN

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
  val sliderWidth get() = max(0, width - myLeftMargin - myRightMargin)

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
    val knobPosition = Math.max(0, Math.min(e.x - myLeftMargin, sliderWidth))
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
    drawKnob(g2d, myLeftMargin + valueToKnobPosition(value), JBUI.scale(7))
  }

  protected abstract fun paintSlider(g2d: Graphics2D)

  private fun drawKnob(g2d: Graphics2D, x: Int, y: Int) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val shadowOffset = +JBUI.scale(KNOB_SHADOW_DISTANCE)
    myPolygonToDraw.reset()
    myPolygonToDraw.addPoint(x - JBUI.scale(KNOB_HALF_WIDTH) + shadowOffset, y - JBUI.scale(KNOB_HALF_HEIGHT) + shadowOffset)
    myPolygonToDraw.addPoint(x + JBUI.scale(KNOB_HALF_WIDTH) + shadowOffset, y - JBUI.scale(KNOB_HALF_HEIGHT) + shadowOffset)
    myPolygonToDraw.addPoint(x + shadowOffset, y + JBUI.scale(KNOB_HALF_HEIGHT) + shadowOffset)
    g2d.color = KNOB_SHADOW_COLOR
    g2d.fill(myPolygonToDraw)

    myPolygonToDraw.reset()
    myPolygonToDraw.addPoint(x - JBUI.scale(KNOB_HALF_WIDTH), y - JBUI.scale(KNOB_HALF_HEIGHT))
    myPolygonToDraw.addPoint(x + JBUI.scale(KNOB_HALF_WIDTH), y - JBUI.scale(KNOB_HALF_HEIGHT))
    myPolygonToDraw.addPoint(x, y + JBUI.scale(KNOB_HALF_HEIGHT))
    g2d.color = KNOB_COLOR
    g2d.fill(myPolygonToDraw)
  }
}
