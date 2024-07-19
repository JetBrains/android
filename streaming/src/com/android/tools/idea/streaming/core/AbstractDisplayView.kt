/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.ui.NotificationHolderPanel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.actions.HardwareInputStateStorage
import com.android.tools.idea.streaming.actions.StreamingHardwareInputAction
import com.intellij.ide.DataManager
import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.htmlComponent
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_ALT
import java.awt.event.KeyEvent.VK_ALT_GRAPH
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_META
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkListener
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Common base class for [com.android.tools.idea.streaming.emulator.EmulatorView] and
 * [com.android.tools.idea.streaming.device.DeviceView].
 */
@Suppress("UseJBColor")
abstract class AbstractDisplayView(val displayId: Int) : ZoomablePanel(), Disposable, KeyboardAwareFocusOwner {

  /** Serial number of the device shown in the view. */
  val deviceSerialNumber: String
    get() = deviceId.serialNumber
  /** ID of the device shown in the view. */
  abstract val deviceId: DeviceId
  /** Area of the window occupied by the device display image in physical pixels. */
  var displayRectangle: Rectangle? = null
    protected set
  /** Orientation of the device display in quadrants counterclockwise. */
  abstract val displayOrientationQuadrants: Int
  /** The difference between [displayOrientationQuadrants] and the orientation according to the internal Android data structures. */
  var displayOrientationCorrectionQuadrants: Int = 0
    protected set
  abstract val apiLevel: Int
  /** Size of the device's native display. */
  internal abstract val deviceDisplaySize: Dimension
  /** The number of the last rendered display frame. */
  var frameNumber: UInt = 0u
    protected set

  private val disconnectedStatePanel = DisconnectedStatePanel()

  private val frameListeners = ContainerUtil.createLockFreeCopyOnWriteList<FrameListener>()

  protected open val hardwareInput: HardwareInput = HardwareInput()

  init {
    background = primaryPanelBackground
    addToCenter(disconnectedStatePanel)
    initializeFocusHandling()
  }

  private fun initializeFocusHandling() {
    isFocusable = true // Must be focusable to receive keyboard events.
    setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, emptySet())
    setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, setOf(KeyStroke.getKeyStroke(VK_TAB, SHIFT_DOWN_MASK)))
    addFocusListener(object : FocusAdapter() {
      override fun focusLost(event: FocusEvent) {
        hardwareInput.resetMetaKeys()
      }
    })
  }

  protected fun drawMultiTouchFeedback(graphics: Graphics2D, displayRectangle: Rectangle, dragging: Boolean) {
    val mouseLocation = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mouseLocation, this)
    val touchPoint = mouseLocation.scaled(screenScale)

    if (!displayRectangle.contains(touchPoint)) {
      return
    }
    val g = graphics.create() as Graphics2D
    g.setRenderingHints(RenderingHints(mapOf(RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                                             RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY)))
    g.clip = displayRectangle

    val centerPoint = Point(displayRectangle.x + displayRectangle.width / 2, displayRectangle.y + displayRectangle.height / 2)
    val mirrorPoint = Point(centerPoint.x * 2 - touchPoint.x, centerPoint.y * 2 - touchPoint.y)
    val r1 = (max(displayRectangle.width, displayRectangle.height) * 0.015).roundToInt()
    val r2 = r1 / 4
    val touchCircle = createCircle(touchPoint, r1)
    val mirrorCircle = createCircle(mirrorPoint, r1)
    val centerCircle = createCircle(centerPoint, r2)
    val clip = Area(displayRectangle).apply {
      subtract(Area(touchCircle))
      subtract(Area(mirrorCircle))
      subtract(Area(centerCircle))
    }
    g.clip = clip
    val darkColor = Color(0, 154, 133, 157)
    val lightColor = Color(255, 255, 255, 157)
    g.color = darkColor
    g.drawLine(touchPoint.x, touchPoint.y, mirrorPoint.x, mirrorPoint.y)
    g.clip = displayRectangle
    g.fillCircle(centerPoint, r2 * 3 / 4)
    val backgroundIntensity = if (dragging) 0.8 else 0.3
    paintTouchBackground(g, touchPoint, r1, darkColor, backgroundIntensity)
    paintTouchBackground(g, mirrorPoint, r1, darkColor, backgroundIntensity)
    g.color = lightColor
    g.drawCircle(touchPoint, r1)
    g.drawCircle(mirrorPoint, r1)
    g.drawCircle(centerPoint, r2)
  }

  private fun paintTouchBackground(g: Graphics2D, center: Point, radius: Int, color: Color, intensity: Double) {
    require(0 < intensity && intensity <= 1)
    val r = radius * 5 / 4
    val intenseColor = Color(color.red, color.green, color.blue, (color.alpha * intensity).roundToInt())
    val subtleColor = Color(color.red, color.green, color.blue, (color.alpha * intensity * 0.15).roundToInt())
    g.paint = RadialGradientPaint(center, r.toFloat(), floatArrayOf(0F, 0.8F, 1F), arrayOf(subtleColor, intenseColor, subtleColor))
    g.fillCircle(center, r)
  }

  private fun createCircle(center: Point, radius: Int): Ellipse2D {
    val diameter = radius * 2.0
    return Ellipse2D.Double((center.x - radius).toDouble(), (center.y - radius).toDouble(), diameter, diameter)
  }

  private fun Graphics.drawCircle(center: Point, radius: Int) {
    drawOval(center.x - radius, center.y - radius, radius * 2, radius * 2)
  }

  private fun Graphics.fillCircle(center: Point, radius: Int) {
    fillOval(center.x - radius, center.y - radius, radius * 2, radius * 2)
  }

  internal fun showLongRunningOperationIndicator(text: String) {
    findLoadingPanel()?.apply {
      setLoadingText(text)
      startLoading()
    }
  }

  internal fun hideLongRunningOperationIndicator() {
    findLoadingPanel()?.stopLoading()
  }

  internal fun hideLongRunningOperationIndicatorInstantly() {
    findLoadingPanel()?.stopLoadingInstantly()
  }

  protected fun showDisconnectedStateMessage(
      message: String, hyperlinkListener: HyperlinkListener? = null, reconnector: Reconnector? = null) {
    hideLongRunningOperationIndicatorInstantly()
    zoom(ZoomType.FIT)
    disconnectedStatePanel.showPanel(message, hyperlinkListener, reconnector)
  }

  protected fun hideDisconnectedStateMessage() {
    disconnectedStatePanel.hidePanel()
  }

  private fun findLoadingPanel(): StreamingLoadingPanel? = findContainingComponent()

  protected fun findNotificationHolderPanel(): NotificationHolderPanel? = findContainingComponent()

  /**
   * Rounds the given value down to an integer if it is above 1, or to the nearest multiple of
   * a small fraction that is close to `value/128` and has the form of `1/2^n`.
   */
  protected fun roundScale(value: Double): Double {
    if (value >= 1) {
      return roundDownIfNecessary(value)
    }
    val logScale = -log2(value).roundToInt() + 7
    val multiplier = 2 shl logScale + 7
    return round(value * multiplier) / multiplier
  }

  protected fun notifyFrameListeners(displayRectangle: Rectangle, frame: BufferedImage) {
    for (listener in frameListeners) {
      listener.frameRendered(frameNumber, displayRectangle, displayOrientationQuadrants, frame)
    }
  }

  /**
   * Adds a [listener] to receive callbacks when the display view has a new frame rendered.
   *
   * The [listener] must return very quickly as it is invoked on the UI thread inside the painting method
   * of the view.
   */
  internal fun addFrameListener(listener: FrameListener) {
    frameListeners.add(listener)
  }

  /** Removes a [listener] so it no longer receives callbacks when the display view has a new frame rendered. */
  internal fun removeFrameListener(listener: FrameListener) {
    frameListeners.remove(listener)
  }

  internal fun interface FrameListener {
    fun frameRendered(frameNumber: UInt, displayRectangle: Rectangle, displayOrientationQuadrants: Int, displayImage: BufferedImage)
  }

  internal fun toDeviceDisplayCoordinates(p: Point): Point? {
    val displayRectangle = displayRectangle ?: return null
    val imageSize = displayRectangle.size.rotatedByQuadrants(displayOrientationQuadrants)
    // Mouse pointer coordinates compensated for the device display rotation.
    val normalized = Point()
    val rotation = when {
      displayOrientationCorrectionQuadrants % 2 == 0 -> displayOrientationQuadrants + displayOrientationCorrectionQuadrants % 4
      else -> displayOrientationQuadrants
    }
    when (rotation) {
      0 -> {
        normalized.x = p.x.scaled(screenScale) - displayRectangle.x
        normalized.y = p.y.scaled(screenScale) - displayRectangle.y
      }
      1 -> {
        normalized.x = displayRectangle.bottom - p.y.scaled(screenScale)
        normalized.y = p.x.scaled(screenScale) - displayRectangle.x
      }
      2 -> {
        normalized.x = displayRectangle.right - p.x.scaled(screenScale)
        normalized.y = displayRectangle.bottom - p.y.scaled(screenScale)
      }
      else -> {  // 3
        normalized.x = p.y.scaled(screenScale) - displayRectangle.y
        normalized.y = displayRectangle.right - p.x.scaled(screenScale)
      }
    }
    // Device display coordinates.
    return normalized.scaledUnbiased(imageSize, deviceDisplaySize)
  }

  protected fun getProject(): Project? =
      CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))

  protected fun isHardwareInputEnabled(): Boolean =
      getProject()?.service<HardwareInputStateStorage>()?.isHardwareInputEnabled(deviceId) ?: false

  final override fun skipKeyEventDispatcher(event: KeyEvent): Boolean {
    if (!isHardwareInputEnabled()) {
      return false
    }
    val stroke = KeyStroke.getKeyStrokeForEvent(event)
    for (keyStroke in KeymapUtil.getKeyStrokes(KeymapUtil.getActiveKeymapShortcuts(StreamingHardwareInputAction.ACTION_ID))) {
      if (stroke == keyStroke) {
        return false
      }
    }
    return true
  }

  internal open fun hardwareInputStateChanged(event: AnActionEvent, enabled: Boolean) {
    if (!enabled) {
      hardwareInput.resetMetaKeys()
    }
  }

  protected open class HardwareInput {

    private var pressedModifierKeys = 0

    fun forwardEvent(event: KeyEvent) {
      event.consume()
      vkToMask[event.keyCode]?.let {
        when (event.id) {
          KEY_PRESSED -> pressedModifierKeys = pressedModifierKeys or it
          KEY_RELEASED -> pressedModifierKeys = pressedModifierKeys and it.inv()
          else -> {}
        }
      }
      sendToDevice(event.id, event.keyCode, event.modifiersEx)
    }

    fun resetMetaKeys() {
      if (pressedModifierKeys == 0) return
      for ((vk, mask) in vkToMask) {
        if (pressedModifierKeys and mask == 0) continue
        pressedModifierKeys = pressedModifierKeys and mask.inv()
        sendToDevice(KEY_RELEASED, vk, pressedModifierKeys)
      }
      pressedModifierKeys = 0
    }

    open fun sendToDevice(id: Int, keyCode: Int, modifiersEx: Int) {}

    companion object {
      private val vkToMask = mapOf(
        VK_SHIFT to SHIFT_DOWN_MASK,
        VK_CONTROL to CTRL_DOWN_MASK,
        VK_ALT to ALT_DOWN_MASK,
        VK_META to META_DOWN_MASK,
        VK_ALT_GRAPH to ALT_GRAPH_DOWN_MASK,
      )
    }
  }

 /** Attempts to restore a lost device connection. */
  protected inner class Reconnector(val reconnectLabel: String, private val progressMessage: String, val reconnect: suspend () -> Unit) {

    /** Starts the reconnection attempt. */
    internal fun start() {
      hideDisconnectedStateMessage()
      showLongRunningOperationIndicator(progressMessage)
      AndroidCoroutineScope(this@AbstractDisplayView).launch {
        reconnect()
      }
    }
  }

  private class DisconnectedStatePanel : JBPanel<DisconnectedStatePanel>(GridBagLayout()) {

    private val message = htmlComponent(text = "", lineWrap = true).apply {
      isOpaque = false
      isFocusable = false
      border = JBUI.Borders.empty()
    }

    val button = JButton("Reconnect")

    init {
      isVisible = false
      val topMargin = 0.45

      val c = GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        gridx = 0
        gridy = 0
        weightx = 1.0
        weighty = topMargin
      }
      add(createFiller(), c)
      c.gridy = 3
      c.weighty = 1 - topMargin
      add(createFiller(), c)

      c.insets = JBUI.insets(10)
      c.weighty = 0.0
      c.fill = GridBagConstraints.HORIZONTAL
      c.gridy = 1
      add(message, c)

      c.gridy = 2
      c.fill = GridBagConstraints.NONE
      add(button, c)
    }

    fun showPanel(messageHtml: String, hyperlinkListener: HyperlinkListener?, reconnector: Reconnector? = null) {
      message.text = "<center>$messageHtml</center>"
      if (hyperlinkListener != null) {
        message.addHyperlinkListener(hyperlinkListener)
      }
      button.apply {
        if (reconnector == null) {
          isVisible = false
        }
        else {
          isVisible = true
          action = object : AbstractAction(reconnector.reconnectLabel) {
            override fun actionPerformed(event: ActionEvent) {
              reconnector.start()
            }
          }
          SwingUtilities.getRootPane(this)?.let { it.defaultButton = this }
        }
      }
      isVisible = true
      revalidate()
    }

    fun hidePanel() {
      for (listener in message.hyperlinkListeners) {
        message.removeHyperlinkListener(listener)
      }
      isVisible = false
      button.action = null
      revalidate()
    }

    private fun createFiller(): JBPanel<*> {
      return JBPanel<JBPanel<*>>()
        .withBorder(JBUI.Borders.empty())
        .withMinimumWidth(0)
        .withMinimumHeight(0)
        .withPreferredSize(0, 0)
        .andTransparent()
    }
  }
}
