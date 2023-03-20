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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.picker.ColorPipetteBase
import com.intellij.ui.picker.MacColorPipette
import com.intellij.util.Alarm
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Transparency
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer
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
// Windows cannot handle a fully transparent color, so use the smallest non-zero alpha value
private val COLOR_TRANSPARENT_BACKGROUND = Color(0, 0, 0, 1)

/**
 * Duration of updating the color of current hovered pixel. The unit is millisecond.
 */
private val DURATION_COLOR_UPDATING = TimeUnit.MILLISECONDS.toMillis(33)

/**
 * Merge update queue for updating cursor asynchronized and only executes the last request.
 *
 * This queue is shared between different [GraphicalColorPipette]s, but there should be only one [GraphicalColorPipette] instance
 * uses this queue at the same time because the system should only have one cursor.
 */
private val cursorUpdateQueue = MergingUpdateQueue("colorpicker.pipette.updatecursor",
                                                   0, // We wait in the task so we don't need to postpone the execution.
                                                   true,
                                                   null,
                                                   null,
                                                   null,
                                                   Alarm.ThreadToUse.POOLED_THREAD)
  .apply { setRestartTimerOnAdd(true) }

/**
 * The [ColorPipette] which picks up the color from monitor.
 */
open class GraphicalColorPipette(private val parent: JComponent) : ColorPipette {
  override val icon: Icon = AllIcons.Ide.Pipette

  override val rolloverIcon: Icon = AllIcons.Ide.Pipette_rollover

  override val pressedIcon: Icon = AllIcons.Ide.Pipette_rollover

  override fun pick(callback: ColorPipette.Callback) = when {
    ColorPipetteBase.canUseMacPipette() -> MacPickerDialog(parent, callback).pick()
    else -> DefaultPickerDialog(parent, callback).pick()
  }
}

class GraphicalColorPipetteProvider : ColorPipetteProvider {
  override fun createPipette(owner: JComponent): ColorPipette = GraphicalColorPipette(owner)
}

private abstract class PickerDialogBase(val parent: JComponent, val callback: ColorPipette.Callback, alwaysOnTop: Boolean) : ImageObserver {

  private val timer = Timer(DURATION_COLOR_UPDATING.toInt()) { updatePipette() }
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

  private var previousColor: Color? = null
  private var previousLoc: Point? = null

  private val picker: Dialog = let {
    val pickerDialog = when (val owner = SwingUtilities.getWindowAncestor(parent)) {
      is Dialog -> JDialog(owner)
      is Frame -> JDialog(owner)
      else -> JDialog(JFrame())
    }

    pickerDialog.isUndecorated = true
    pickerDialog.isAlwaysOnTop = alwaysOnTop
    // Don't use JBDimension here since we want to use Pixel as unit.
    pickerDialog.size = Dimension(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE + COLOR_CODE_RECTANGLE_GAP + COLOR_CODE_RECTANGLE_HEIGHT)
    pickerDialog.background = COLOR_TRANSPARENT_BACKGROUND
    pickerDialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val rootPane = pickerDialog.rootPane
    rootPane.putClientProperty("Window.shadow", false)

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

    pickerDialog.addMouseListener(mouseAdapter)
    pickerDialog.addMouseMotionListener(mouseAdapter)

    pickerDialog.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
          KeyEvent.VK_ESCAPE -> cancelPipette()
          KeyEvent.VK_ENTER -> pickDone()
        }
      }
    })

    val emptyImage = UIUtil.createImage(pickerDialog, 1, 1, Transparency.TRANSLUCENT)
    pickerDialog.cursor = parent.toolkit.createCustomCursor(emptyImage, Point(0, 0), CURSOR_NAME)

    pickerDialog
  }

  fun pick() {
    picker.isVisible = true
    timer.start()
  }

  override fun imageUpdate(img: Image, flags: Int, x: Int, y: Int, width: Int, height: Int) = false

  private fun cancelPipette() {
    timer.stop()

    picker.isVisible = false
    UIUtil.dispose(picker)

    callback.cancel()
  }

  private fun pickDone() {
    timer.stop()

    val pointerInfo = MouseInfo.getPointerInfo()
    val location = pointerInfo.location
    val capture = captureScreen(picker, Rectangle(location.x, location.y, 1, 1))
    val pickedColor = if (capture == null) {
      Logger.getInstance(PickerDialogBase::class.java).warn("Cannot capture screen, use ${Color.WHITE} instead")
      Color.WHITE
    }
    else {
      Color(capture.getRGB(0, 0))
    }
    picker.isVisible = false
    UIUtil.dispose(picker)

    callback.picked(pickedColor)
  }

  private fun updatePipette() {
    if (picker.isShowing) {
      val pointerInfo = MouseInfo.getPointerInfo()
      val mouseLoc = pointerInfo.location
      picker.setLocation(mouseLoc.x - ZOOM_RECTANGLE_SIZE / 2, mouseLoc.y - ZOOM_RECTANGLE_SIZE / 2)

      captureRect.setBounds(mouseLoc.x - SCREEN_CAPTURE_SIZE / 2,
                            mouseLoc.y - SCREEN_CAPTURE_SIZE / 2,
                            SCREEN_CAPTURE_SIZE,
                            SCREEN_CAPTURE_SIZE)
      val capture = captureScreen(picker, captureRect) ?: return
      val pickedColor = Color(capture.getRGB(SCREEN_CAPTURE_SIZE / 2 + 1, SCREEN_CAPTURE_SIZE / 2 + 1))

      if (previousLoc != mouseLoc || previousColor != pickedColor) {
        previousLoc = mouseLoc
        previousColor = pickedColor

        val graphics = image.graphics as Graphics2D

        // Clear the cursor graphics
        graphics.composite = AlphaComposite.Src
        graphics.color = TRANSPARENT_COLOR
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.drawImage(capture, 0, 0, ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE, this)

        // Draw border
        graphics.composite = AlphaComposite.SrcOver
        graphics.color = PIPETTE_BORDER_COLOR
        graphics.drawRect(0, 0, ZOOM_RECTANGLE_SIZE - 1, ZOOM_RECTANGLE_SIZE - 1)
        graphics.color = INDICATOR_BOUND_COLOR
        graphics.drawRect(INDICATOR_BOUND_START, INDICATOR_BOUND_START, INDICATOR_BOUND_SIZE, INDICATOR_BOUND_SIZE)

        // Draw color code text
        val originalFont = graphics.font
        // Ignore alpha value since it is always 0xFF when picking color on the screen.
        val colorValueString = String.format("#%06X", (pickedColor.rgb and 0x00FFFFFF))

        val font = StartupUiUtil.labelFont.deriveFont(COLOR_VALUE_FONT_SIZE)
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
        graphics.font = originalFont

        val parentComponent = parent

        cursorUpdateQueue.queue(object : Update("model.render", LOW_PRIORITY) {
          override fun canEat(update: Update): Boolean {
            return this == update
          }

          override fun run() {
            try {
              // According to the documentation, Toolkit.createCustomCursor() may hang due to some unexpected cases.
              // We future here to avoid potential hanging issue.
              val future = ApplicationManager.getApplication().executeOnPooledThread(
                Callable<Cursor> { parentComponent.toolkit.createCustomCursor(image, center, CURSOR_NAME) })
              // If it spent more than DURATION_COLOR_UPDATING milliseconds to get the cursor, we drop it and just use the old cursor.
              picker.cursor = future.get(DURATION_COLOR_UPDATING, TimeUnit.MILLISECONDS)
            }
            catch (e: Exception) {
              // Handles IndexOutOfBoundsException and HeadlessException of calling toolkit.createCustomCursor
              // This also handles the TimeoutException of future task.
              if (e is NullPointerException) {
                // b/128599052: Component.getToolKit() doesn't guarantee the returned value is not null and we saw a reported bug has NPE
                // issue. We catch it here and log for future debugging purpose.
                Logger.getInstance(GraphicalColorPipette::class.java).error(e.message)
              }
            }
          }
        })
      }
    }
  }

  abstract fun captureScreen(belowWindow: Window?, rect: Rectangle): BufferedImage?
}

private class DefaultPickerDialog(parent: JComponent, callback: ColorPipette.Callback) : PickerDialogBase(parent, callback, false) {
  private val graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
  private val graphicsToRobots: Map<GraphicsDevice, Robot> = graphicsDevices.associateWith { device -> Robot(device) }

  override fun captureScreen(belowWindow: Window?, rect: Rectangle): BufferedImage? {
    try {
      val mousePoint = MouseInfo.getPointerInfo().location
      val device = graphicsDevices.firstOrNull { it.defaultConfiguration?.bounds?.contains(mousePoint) ?: false } ?: return null
      val robot = graphicsToRobots[device] ?: return null
      return robot.createScreenCapture(rect)
    }
    catch (e: Exception) {
      Logger.getInstance(DefaultPickerDialog::class.java).warn("Cannot capture the image from screen")
      return null
    }
  }
}

private class MacPickerDialog(parent: JComponent, callback: ColorPipette.Callback) : PickerDialogBase(parent, callback, true) {

  override fun captureScreen(belowWindow: Window?, rect: Rectangle): BufferedImage? = MacColorPipette.captureScreen(belowWindow, rect)
}
