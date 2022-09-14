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
package com.android.tools.idea.emulator

import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt


/**
 * Common base class for [EmulatorView] and [com.android.tools.idea.device.DeviceView].
 */
abstract class AbstractDisplayView(val displayId: Int) : ZoomablePanel(), Disposable {

  private val disconnectedStateMessage = JBLabel("", SwingConstants.CENTER).apply { font = font.deriveFont(font.size * 1.2F) }
  private val reconnectButton = JButton("Reconnect")

  private val disconnectedStatePanel = Box.createVerticalBox().apply {
    isVisible = false
    add(Box.createVerticalGlue())
    disconnectedStateMessage.alignmentX = CENTER_ALIGNMENT
    add(disconnectedStateMessage)
    add(Box.createVerticalStrut(JBUIScale.scale(20)))
    reconnectButton.alignmentX = CENTER_ALIGNMENT
    add(reconnectButton)
    add(Box.createVerticalGlue())
  }

  init {
    background = primaryPanelBackground
    addToCenter(disconnectedStatePanel)

    isFocusable = true // Must be focusable to receive keyboard events.
    focusTraversalKeysEnabled = false // Receive focus traversal keys to send them to the device.
  }

  /**
   * Processes a focus traversal key event by passing it to the keyboard focus manager.
   */
  protected fun traverseFocusLocally(event: KeyEvent) {
    if (!focusTraversalKeysEnabled) {
      focusTraversalKeysEnabled = true
      try {
        getCurrentKeyboardFocusManager().processKeyEvent(this, event)
      }
      finally {
        focusTraversalKeysEnabled = false
      }
    }

    revalidate()
    repaint()
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

  fun showLongRunningOperationIndicator(text: String) {
    findLoadingPanel()?.apply {
      setLoadingText(text)
      startLoading()
    }
  }

  fun hideLongRunningOperationIndicator() {
    findLoadingPanel()?.stopLoading()
  }

  fun hideLongRunningOperationIndicatorInstantly() {
    findLoadingPanel()?.stopLoadingInstantly()
  }

  protected fun showDisconnectedStateMessage(message: String, reconnector: Reconnector? = null) {
    hideLongRunningOperationIndicatorInstantly()
    disconnectedStateMessage.text = message
    reconnectButton.apply {
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
    disconnectedStatePanel.isVisible = true
    revalidate()
  }

  protected fun hideDisconnectedStateMessage() {
    hideLongRunningOperationIndicatorInstantly()
    disconnectedStatePanel.isVisible = false
    reconnectButton.apply {
      action = null
    }
    revalidate()
  }

  private fun findLoadingPanel(): EmulatorLoadingPanel? = findContainingComponent()

  protected fun findNotificationHolderPanel(): NotificationHolderPanel? = findContainingComponent()

  private inline fun <reified T : JComponent> findContainingComponent(): T? {
    var component = parent
    while (component != null) {
      if (component is T) {
        return component
      }
      component = component.parent
    }
    return null
  }

  /** Rounds the given value down to an integer if it is above 1, or to the nearest multiple of 1/128 if it is below 1. */
  protected fun roundScale(value: Double): Double {
    return if (value >= 1) floor(value) else round(value * 128) / 128
  }

  /** Attempts to restore a lost device connection. */
  protected inner class Reconnector(val reconnectLabel: String, private val progressMessage: String, val reconnect: suspend () -> Unit) {

    /** Starts the reconnection attempt. */
    fun start() {
      hideDisconnectedStateMessage()
      showLongRunningOperationIndicator(progressMessage)
      AndroidCoroutineScope(this@AbstractDisplayView).launch {
        reconnect()
      }
    }
  }
}
