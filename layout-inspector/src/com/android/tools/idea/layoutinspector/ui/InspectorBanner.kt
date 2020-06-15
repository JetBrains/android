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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.StatusNotification
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max

private const val HORIZONTAL_BORDER_SIZE = 6
private const val VERTICAL_BORDER_SIZE = 3

/**
 * A banner for showing notifications in the Layout Inspector.
 */
class InspectorBanner(project: Project) : JPanel(BorderLayout()) {
  private val bannerBackground = JBColor.namedColor("Notification.background", JBColor(0xfff8d1, 0x1d3857))
  private val bannerForeground = JBColor.namedColor("Notification.foreground", JBColor(0x555555, 0xaaaaaa))
  @VisibleForTesting
  val text = JLabel()
  private val actionLayout = FlowLayout(FlowLayout.CENTER, JBUI.scale(
    HORIZONTAL_BORDER_SIZE), 0)
  private val actionPanel = JPanel(actionLayout)
  private var classInitialized = true

  init {
    isVisible = false
    background = bannerBackground
    text.foreground = bannerForeground
    actionPanel.background = bannerBackground
    add(text, BorderLayout.WEST)
    add(actionPanel, BorderLayout.EAST)
    applyUISettings()
    InspectorBannerService.getInstance(project).notificationListeners.add(::applyNewNotification)
  }

  private fun applyUISettings() {
    border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    val borderSpacing = JBUI.Borders.empty(VERTICAL_BORDER_SIZE, HORIZONTAL_BORDER_SIZE)
    actionPanel.border = borderSpacing
    actionLayout.hgap = JBUI.scale(VERTICAL_BORDER_SIZE)
    text.border = borderSpacing
  }

  override fun updateUI() {
    super.updateUI()
    // This method is called from the initializer of JPanel, avoid NPE be delaying until this class is fully initialized.
    if (classInitialized) {
      applyUISettings()
    }
  }

  private fun applyNewNotification(statusNotification: StatusNotification?) {
    isVisible = statusNotification != null
    val notification = statusNotification ?: return
    text.text = notification.message
    actionPanel.removeAll()
    notification.actions.forEach { action ->
      val actionLabel = HyperlinkLabel(action.templateText, JBColor.BLUE)
      actionLabel.addHyperlinkListener {
        val context = DataManager.getInstance().getDataContext(actionLabel)
        val presentation = action.templatePresentation.clone()
        val event = AnActionEvent(it.inputEvent, context, ActionPlaces.NOTIFICATION, presentation, ActionManager.getInstance(), 0)
        action.actionPerformed(event)
      }
      actionPanel.add(actionLabel)
    }
  }

  /**
   * Make sure the text doesn't overlap the action label
   */
  override fun doLayout() {
    super.doLayout()
    if (text.x + text.width > actionPanel.x) {
      text.size = Dimension(max(0, actionPanel.x - text.x), text.size.height)
    }
  }
}
