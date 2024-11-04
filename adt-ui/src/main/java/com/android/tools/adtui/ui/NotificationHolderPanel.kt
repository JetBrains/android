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
package com.android.tools.adtui.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.Animator
import com.intellij.util.ui.ImageUtil
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

/** A panel that can display notifications at the top. */
class NotificationHolderPanel(private val contentPanel: Component) : JBLayeredPane() {

  private var fadeOutNotificationPopup: NotificationPopup? = null
  private var animator: Animator? = null

  init {
    border = JBUI.Borders.empty()
    setLayer(contentPanel, DEFAULT_LAYER)
    add(contentPanel)
  }

  override fun doLayout() {
    var y = 0
    for (child in components) {
      if (child === contentPanel) {
        child.setBounds(0, 0, width, height)
      }
      else {
        val childHeight = child.preferredSize.height
        child.setBounds(0, y, width, childHeight)
        y += childHeight
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    return contentPanel.preferredSize ?: super.getPreferredSize()
  }

  override fun paintChildren(g: Graphics) {
    if (componentCount > 1) {
      // Paint off-screen first to prevent flicker.
      val image = ImageUtil.createImage(g, width, height, BufferedImage.TYPE_INT_ARGB)
      val g2d: Graphics2D = image.createGraphics()
      super.paintChildren(g2d)
      g2d.dispose()
      // Render the off-screen image.
      val rect = Rectangle(image.getWidth(null), image.getHeight(null))
      UIUtil.drawImage(g, image, rect, rect, null)
    }
    else {
      super.paintChildren(g)
    }
  }

  /**
   * Adds a notification panel. If the [notificationPanel] has a close action, that action has to make
   * sure that the notification is removed when the action is executed.
   */
  fun addNotification(notificationPanel: EditorNotificationPanel) {
    setLayer(notificationPanel, POPUP_LAYER)
    add(notificationPanel)
    revalidate()
  }

  /** Removes the given notification panel. */
  fun removeNotification(notificationPanel: EditorNotificationPanel) {
    remove(notificationPanel)
    revalidate()
    repaint()
  }

  /**
   * Shows a fade-out notification with the given text. If [status] is null, the notification
   * is shown with info background and without an icon. Otherwise, it is shown with the background
   * and icon corresponding to [status].
   */
  fun showFadeOutNotification(text: String, status: EditorNotificationPanel.Status? = null) {
    hideFadeOutNotification()
    val notificationPopup = createFadeOutNotificationPopup(status)
    fadeOutNotificationPopup = notificationPopup
    notificationPopup.notificationPanel.text = text
    startFadeOutAnimation()
    revalidate()
  }

  /** Hides the fade-out notification if any. */
  fun hideFadeOutNotification() {
    stopFadeOutAnimation()
    hideFadeOutNotificationPopup()
  }

  private fun createFadeOutNotificationPopup(severity: EditorNotificationPanel.Status?): NotificationPopup {
    val notificationPanel = severity?.let { EditorNotificationPanel(it) } ?: EditorNotificationPanel(HintUtil.INFORMATION_COLOR_KEY)
    val popup = NotificationPopup(notificationPanel)
    setLayer(popup, POPUP_LAYER)
    addImpl(popup, null, 0)
    return popup
  }

  private fun hideFadeOutNotificationPopup() {
    fadeOutNotificationPopup?.let {
      remove(it)
      fadeOutNotificationPopup = null
      revalidate()
    }
  }

  private fun startFadeOutAnimation() {
    animator?.dispose()
    fadeOutNotificationPopup?.alpha = 1.0F
    animator = FadeOutAnimator().apply { resume() }
  }

  private fun stopFadeOutAnimation() {
    animator?.dispose()
    animator = null
  }

  private class NotificationPopup(val notificationPanel: EditorNotificationPanel) : BorderLayoutPanel() {
    var alpha = 1.0F

    init {
      border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.BOTTOM)
      isOpaque = false
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
      val popup = fadeOutNotificationPopup ?: return
      val alpha = cos(0.5 * PI * frame / totalFrames).toFloat()
      if (abs(alpha - popup.alpha) >= 0.005) {
        popup.alpha = alpha
        popup.paintImmediately(0, 0, popup.width, popup.height)
      }
    }

    override fun paintCycleEnd() {
      // In a headless or a test environment paintCycleEnd is called by the Animator's constructor.
      // Don't hide the notification is that case.
      if (!skipAnimation()) {
        hideFadeOutNotificationPopup()
      }
      dispose()
    }

    override fun dispose() {
      super.dispose()
      animator = null
    }

    /** Copied from the [Animator] class where it is unfortunately private. */
    private fun skipAnimation(): Boolean {
      if (GraphicsEnvironment.isHeadless()) {
        return true
      }
      val app = ApplicationManager.getApplication()
      return app != null && app.isUnitTestMode
    }
  }
}