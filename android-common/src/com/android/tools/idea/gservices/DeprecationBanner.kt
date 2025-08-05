/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gservices

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Banner
import java.awt.BorderLayout
import java.awt.EventQueue.invokeLater
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingConstants

/** Banner for showing the deprecated state. */
abstract class DeprecationBanner(
  private val project: Project,
  val deprecationData: DevServicesDeprecationData,
  private val moveActions: Boolean,
  private val closeAction: () -> Unit = {},
) :
  EditorNotificationPanel(
    if (deprecationData.isDeprecated()) {
      Status.Warning
    } else {
      Status.Error
    }
  ) {
  init {
    text = "<html>${deprecationData.description}</html>"
    isOpaque = true
    var hasAction = false
    if (deprecationData.showUpdateAction) {
      hasAction = true
      createActionLabel("Update Android Studio") {
        UpdateChecker.updateAndShowResult(project)
        trackUpdateClicked()
      }
    }
    if (deprecationData.moreInfoUrl.isNotEmpty()) {
      hasAction = true
      createActionLabel("More info") {
        BrowserUtil.browse(deprecationData.moreInfoUrl)
        trackMoreInfoClicked()
      }
    }
    if (moveActions && hasAction) {
      moveActionLabels()
    }

    setCloseAction {
      isVisible = false
      closeAction()
      trackBannerDismissed()
    }

    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          this@DeprecationBanner.preferredSize =
            JBDimension(this@DeprecationBanner.preferredWidth, getCorrectedPreferredHeight())
        }
      }
    )

    invokeLater { trackUserNotified() }
  }

  /**
   * Calculates the height of text label, links panel and their respective insets. Adds an extra
   * buffer to the height for spacing.
   */
  fun getCorrectedPreferredHeight() =
    if (moveActions) {
      myLabel.getPreferredFullHeight() + myLinksPanel.getPreferredFullHeight() + 20.scaled
    } else {
      myLabel.getPreferredFullHeight() + 20.scaled
    }

  private fun JComponent.getPreferredFullHeight(): Int =
    preferredHeight + insets.top + insets.bottom

  /**
   * Move the action labels to the south of the banner.
   *
   * TODO (b/394364819) layout action labels with stable APIs
   */
  private fun moveActionLabels() {
    val parent = myLinksPanel.parent
    if (parent.layout is BorderLayout) {
      myLabel.verticalTextPosition = SwingConstants.TOP
      parent.add(myLinksPanel, BorderLayout.SOUTH)
      // Align firstActionLabel vertically with myLabel.
      myLinksPanel.border =
        JBUI.Borders.empty(2, myLabel.icon.iconWidth + myLabel.iconTextGap - 2, 0, 0)
    }
  }

  override fun paintBorder(g: Graphics) {
    super.paintBorder(g)
    with(g as Graphics2D) {
      val color =
        if (deprecationData.isDeprecated()) {
          Banner.WARNING_BORDER_COLOR
        } else {
          Banner.ERROR_BORDER_COLOR
        }
      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.color = color
      drawRect((-5).scaled, 0, width + 10.scaled, height - 1.scaled)
    }
  }

  private val Int.scaled: Int
    get() = JBUI.scale(this)

  // Functions that the implementing class need to implement for metric tracking purposes.
  abstract fun trackUserNotified()

  abstract fun trackUpdateClicked()

  abstract fun trackMoreInfoClicked()

  abstract fun trackBannerDismissed()
}
