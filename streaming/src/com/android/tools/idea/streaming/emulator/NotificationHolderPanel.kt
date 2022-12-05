/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.Animator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

private const val FADEOUT_TIME_MILLIS = 5000
private const val TOTAL_FRAMES = 150

/**
 * A panel that can display a notification at the top.
 */
class NotificationHolderPanel(contentPanel: Component) : JBLayeredPane() {
  private val notificationContent = EditorNotificationPanel(HintUtil.INFORMATION_COLOR_KEY)
  private val notificationPopup = NotificationPopup(notificationContent)
  private var animator: Animator? = null
  private var notificationVisible = false

  init {
    border = JBUI.Borders.empty()
    setLayer(contentPanel, DEFAULT_LAYER)
    add(contentPanel)
    setLayer(notificationPopup, POPUP_LAYER)
    notificationPopup.isOpaque = false
  }

  override fun doLayout() {
    for (component in components) {
      component.setBounds(0, 0, width, if (component === notificationPopup) notificationPopup.preferredSize.height else height)
    }
  }

  override fun getPreferredSize(): Dimension {
    val mainComponent = components.find { it !== notificationPopup }
    return mainComponent?.preferredSize ?: super.getPreferredSize()
  }

  override fun paintChildren(g: Graphics) {
    if (notificationVisible) {
      // Paint off-screen first to prevent flicker.
      val image = UIUtil.createImage(this, width, height, BufferedImage.TYPE_INT_ARGB)
      val g2d: Graphics2D = image.createGraphics()
      super.paintChildren(g)
      g2d.dispose()
      // Render the off-screen image.
      UIUtil.drawImage(g, image, Rectangle(width, height), Rectangle(image.getWidth(null), image.getHeight(null)), null)
    }
    else {
      super.paintChildren(g)
    }
  }

  fun showNotification(text: String) {
    notificationContent.text = text
    if (!notificationVisible) {
      add(notificationPopup)
      notificationVisible = true
    }
    startAnimation()
    revalidate()
  }

  fun hideNotification() {
    stopAnimation()
    hideNotificationPopup()
  }

  private fun hideNotificationPopup() {
    if (notificationVisible) {
      remove(notificationPopup)
      notificationVisible = false
      revalidate()
    }
  }

  private fun startAnimation() {
    animator?.let(Disposer::dispose)
    notificationPopup.alpha = 1.0F
    animator = FadeOutAnimator().apply { resume() }
  }

  private fun stopAnimation() {
    animator?.let(Disposer::dispose)
    animator = null
  }

  private class NotificationPopup(notificationPanel: EditorNotificationPanel) : BorderLayoutPanel() {
    var alpha = 1.0F

    init {
      border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM)
      addToCenter(notificationPanel)
    }

    override fun paint(g: Graphics) {
      if (alpha == 1.0F) {
        super.paint(g)
      }
      else {
        val g2d = g.create() as Graphics2D
        try {
          g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
          g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
          super.paint(g2d)
        }
        finally {
          g2d.dispose()
        }
      }
    }
  }

  private inner class FadeOutAnimator : Animator("FadeOutAnimator", TOTAL_FRAMES, FADEOUT_TIME_MILLIS, false) {

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      val alpha = cos(0.5 * PI * frame / totalFrames).toFloat()
      if (abs(alpha - notificationPopup.alpha) >= 0.005) {
        notificationPopup.alpha = alpha
        notificationPopup.paintImmediately(0, 0, notificationPopup.width, notificationPopup.height)
      }
    }

    override fun paintCycleEnd() {
      // In a headless environment paintCycleEnd is called by the Animator's constructor. Don't hide the notification is that case.
      if (!GraphicsEnvironment.isHeadless()) {
        hideNotificationPopup()
      }
      Disposer.dispose(this)
    }

    override fun dispose() {
      super.dispose()
      animator = null
    }
  }
}