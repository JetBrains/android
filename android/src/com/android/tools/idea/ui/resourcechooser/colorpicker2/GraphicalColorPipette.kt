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

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Transparency
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import javax.swing.Timer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * The size of captured screen area. It is same as the number of pixels are caught.<br>
 * The selected pixel is the central one, so this value must be odd.
 */
private const val SCREEN_CAPTURE_SIZE = 11
/**
 * The size of zoomed rectangle which shows the captured screen.
 */
private const val ZOOM_RECTANGLE_SIZE = 48

private const val COLOR_CODE_RECTANGLE_GAP = 4

private const val COLOR_CODE_RECTANGLE_HEIGHT = 12

private val PIPETTE_BORDER_COLOR = Color.BLACK
private val INDICATOR_BOUND_COLOR = Color.RED
/**
 * The left/top bound of selected pixel in zoomed rectangle.
 */
private const val INDICATOR_BOUND_START = ZOOM_RECTANGLE_SIZE * (SCREEN_CAPTURE_SIZE / 2) / SCREEN_CAPTURE_SIZE
/**
 * The width/height of selected pixel in zoomed rectangle.
 */
private const val INDICATOR_BOUND_SIZE = ZOOM_RECTANGLE_SIZE * (SCREEN_CAPTURE_SIZE / 2 + 1) / SCREEN_CAPTURE_SIZE - INDICATOR_BOUND_START

private val TRANSPARENT_COLOR = Color(0, true)

private const val CURSOR_NAME = "GraphicalColorPicker"

private val COLOR_VALUE_TEXT_COLOR = Color.WHITE
private const val COLOR_VALUE_FONT_SIZE = 9.2f
private val COLOR_VALUE_BACKGROUND = Color(0x80, 0x80, 0x80, 0xB0)

/**
 * Duration of updating the color of current hovered pixel. The unit is millisecond.
 */
private const val DURATION_COLOR_UPDATING = 33

/**
 * The [ColorPipette] which picks up the color from monitor.
 */
open class GraphicalColorPipette(private val parent: JComponent) : ColorPipette {
  override val icon: Icon = AllIcons.Ide.Pipette

  override val rolloverIcon: Icon = AllIcons.Ide.Pipette_rollover

  override val pressedIcon: Icon = AllIcons.Ide.Pipette_rollover

  override fun pick(callback: ColorPipette.Callback) = PickerDialog(parent, callback).pick()
}

class GraphicalColorPipetteProvider : ColorPipetteProvider {
  override fun createPipette(owner: JComponent): ColorPipette = GraphicalColorPipette(owner)
}

private class PickerDialog(val parent: JComponent, val callback: ColorPipette.Callback) : ImageObserver {

  private val timer = Timer(DURATION_COLOR_UPDATING) { updatePipette() }
  private val center = Point(ZOOM_RECTANGLE_SIZE / 2, ZOOM_RECTANGLE_SIZE / 2)
  private val captureRect = Rectangle()

  private val image: BufferedImage = let {
    val width = ZOOM_RECTANGLE_SIZE
    val height = ZOOM_RECTANGLE_SIZE + COLOR_CODE_RECTANGLE_GAP + COLOR_CODE_RECTANGLE_HEIGHT
    val image = parent.graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT)
    val graphics2d = image.graphics as Graphics2D
    graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    image
  }

  private val robot = Robot()
  private var previousColor: Color? = null
  private var previousLoc: Point? = null

  private val picker: Dialog = let {
    val owner = SwingUtilities.getWindowAncestor(parent)
    val pickerFrame = when (owner) {
      is Dialog -> JDialog(owner)
      is Frame -> JDialog(owner)
      else -> JDialog(JFrame())
    }

    pickerFrame.isUndecorated = true
    pickerFrame.isAlwaysOnTop = true
    pickerFrame.size = Dimension(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE + COLOR_CODE_RECTANGLE_GAP + COLOR_CODE_RECTANGLE_HEIGHT)
    pickerFrame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val rootPane = pickerFrame.rootPane
    rootPane.putClientProperty("Window.shadow", false)
    rootPane.border = JBUI.Borders.empty()

    val mouseAdapter = object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        e.consume()
        when {
          SwingUtilities.isLeftMouseButton(e) -> pickDone()
          SwingUtilities.isRightMouseButton(e) -> cancelPipette()
          else -> Unit
        }
      }

      override fun mouseMoved(e: MouseEvent) = updatePipette()
    }

    pickerFrame.addMouseListener(mouseAdapter)
    pickerFrame.addMouseMotionListener(mouseAdapter)

    pickerFrame.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
          KeyEvent.VK_ESCAPE -> cancelPipette()
          KeyEvent.VK_ENTER -> pickDone()
        }
      }
    })

    pickerFrame
  }

  fun pick() {
    picker.isVisible = true
    timer.start()
    // it seems like it's the lowest value for opacity for mouse events to be processed correctly
    WindowManager.getInstance().setAlphaModeRatio(picker, if (SystemInfo.isMac) 0.95f else 0.99f)
  }

  override fun imageUpdate(img: Image, flags: Int, x: Int, y: Int, width: Int, height: Int) = false

  private fun cancelPipette() {
    timer.stop()

    picker.isVisible = false
    picker.dispose()

    callback.cancel()
  }

  private fun pickDone() {
    timer.stop()

    val pointerInfo = MouseInfo.getPointerInfo()
    val location = pointerInfo.location
    val pickedColor = robot.getPixelColor(location.x, location.y)
    picker.isVisible = false

    callback.picked(pickedColor)
  }

  private fun updatePipette() {
    if (picker.isShowing) {
      val pointerInfo = MouseInfo.getPointerInfo()
      val mouseLoc = pointerInfo.location
      picker.setLocation(mouseLoc.x - ZOOM_RECTANGLE_SIZE / 2, mouseLoc.y - ZOOM_RECTANGLE_SIZE / 2)

      val pickedColor = robot.getPixelColor(mouseLoc.x, mouseLoc.y)

      if (previousLoc != mouseLoc || previousColor != pickedColor) {
        previousLoc = mouseLoc
        previousColor = pickedColor

        val halfPixelNumber = SCREEN_CAPTURE_SIZE / 2
        captureRect.setBounds(mouseLoc.x - halfPixelNumber, mouseLoc.y - halfPixelNumber, SCREEN_CAPTURE_SIZE, SCREEN_CAPTURE_SIZE)

        val graphics = image.graphics as Graphics2D

        // Clear the cursor graphics
        graphics.composite = AlphaComposite.Src
        graphics.color = TRANSPARENT_COLOR
        graphics.fillRect(0, 0, image.width, image.height)

        val capture = robot.createScreenCapture(captureRect)
        graphics.drawImage(capture, 0, 0, ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE, this)

        graphics.composite = AlphaComposite.SrcOver
        graphics.color = PIPETTE_BORDER_COLOR
        graphics.drawRect(0, 0, ZOOM_RECTANGLE_SIZE - 1, ZOOM_RECTANGLE_SIZE - 1)
        graphics.color = INDICATOR_BOUND_COLOR
        graphics.drawRect(INDICATOR_BOUND_START, INDICATOR_BOUND_START, INDICATOR_BOUND_SIZE, INDICATOR_BOUND_SIZE)

        val originalFont = graphics.font
        // Ignore alpha value since it is always 0xFF when picking color on the screen.
        val colorValueString = String.format("#%06X", (pickedColor.rgb and 0x00FFFFFF))

        val font = UIUtil.getLabelFont().deriveFont(COLOR_VALUE_FONT_SIZE)
        val tracking = 0.08
        graphics.font = font.deriveFont(mapOf(TextAttribute.TRACKING to tracking))
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        graphics.color = COLOR_VALUE_BACKGROUND
        graphics.fillRect(0, ZOOM_RECTANGLE_SIZE + COLOR_CODE_RECTANGLE_GAP, ZOOM_RECTANGLE_SIZE, COLOR_CODE_RECTANGLE_HEIGHT)
        graphics.color = COLOR_VALUE_TEXT_COLOR

        val fm = graphics.fontMetrics
        val rect = fm.getStringBounds(colorValueString, graphics)
        val textWidth = rect.width * (1.0 + tracking)
        val x = (ZOOM_RECTANGLE_SIZE / 2 - textWidth / 2).toInt()
        graphics.drawString(colorValueString, x, ZOOM_RECTANGLE_SIZE + COLOR_CODE_RECTANGLE_GAP + fm.ascent)

        picker.cursor = parent.toolkit.createCustomCursor(image, center, CURSOR_NAME)

        graphics.font = originalFont

        callback.update(pickedColor)
      }
    }
  }
}
