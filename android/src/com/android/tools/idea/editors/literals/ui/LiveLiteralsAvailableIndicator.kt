/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.editors.literals.ui

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.editors.literals.LiveLiteralsApplicationConfiguration
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.noteComponent
import com.intellij.ui.layout.panel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent

private val AVAILABLE_ICON = AllIcons.Actions.Lightning
private val NOT_AVAILABLE_ICON = IconUtil.desaturate(AllIcons.Actions.Lightning)
private const val WIDGET_ID = "LiveLiteralsWidget"

private class LiveLiteralsAvailableIndicator(private val liveLiteralsService: LiveLiteralsService) :
  CustomStatusBarWidget, StatusBarWidget.Multiframe, ClickableLabel() {
  private var statusBar: StatusBar? = null

  init {
    addActionListener {
      if (!liveLiteralsService.isEnabled) {
        // When the feature is not enable, this widget should not be visible so log a warning
        Logger.getInstance(LiveLiteralsAvailableIndicator::class.java).warn("Live Literals feature is not enabled")
        return@addActionListener
      }
      if (!liveLiteralsService.isAvailable) return@addActionListener

      showAvailablePopup(true)
    }
  }

  override fun ID(): String = WIDGET_ID
  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  override fun getComponent(): JComponent = this

  override fun copy(): StatusBarWidget = LiveLiteralsAvailableIndicator(liveLiteralsService)
  override fun getToolTipText(): String = if (liveLiteralsService.isAvailable)
    message("live.literals.is.enabled")
  else message("live.literals.is.disabled")

  fun showAvailablePopup(invokedByUser: Boolean = false) {
    val popup = LightCalloutPopup()

    val contentPanel = panel {
      val highlightAction = ActionManager.getInstance().getAction("Compose.Live.Literals.ToggleHighlight")
      val shortcutLabel = highlightAction.shortcutSet.shortcuts.firstOrNull()?.let {
        " (${KeymapUtil.getShortcutText(it)})"
      } ?: ""
      val toggleLiteralsActionName = "\"${highlightAction.templateText}\"$shortcutLabel"

      row {
        label(message("live.literals.is.available", toggleLiteralsActionName))
        val disableLink = noteComponent(message("live.literals.is.available.disable.hint")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, message("live.literals.configurable.display.name"))
          popup.close()
        }.apply {
          isOpaque = false
        }
        createNoteOrCommentRow(disableLink)
      }
      if (!invokedByUser) {
        row {
          right {
            link(message("live.literals.is.available.do.not.show")) {
              LiveLiteralsApplicationConfiguration.getInstance().showAvailablePopup = false
              popup.close()
            }
          }
        }
      }
    }.apply {
      border = JBUI.Borders.empty(9)
      isOpaque = false
    }

    popup.show(contentPanel, this, Point(0, 0))
  }

  override fun getIcon(): Icon? = when {
    !liveLiteralsService.isEnabled -> null // Live literals is completely disabled
    liveLiteralsService.isAvailable -> AVAILABLE_ICON
    else -> NOT_AVAILABLE_ICON
  }

  override fun dispose() {
    statusBar = null
  }
}

class LiveLiteralsAvailableIndicatorFactory : StatusBarWidgetFactory {
  override fun getId(): String = "LiveLiteralsAvailableWidget"
  override fun getDisplayName(): String = message("live.literals.tracking.display.name")
  override fun isAvailable(project: Project): Boolean = LiveLiteralsService.getInstance(project).isEnabled
  override fun createWidget(project: Project): StatusBarWidget = LiveLiteralsAvailableIndicator(LiveLiteralsService.getInstance(project))
  override fun disposeWidget(widget: StatusBarWidget) {}
  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  companion object {
    fun showIsAvailablePopup(project: Project) {
      (WindowManager.getInstance().getStatusBar(project).getWidget(WIDGET_ID) as? LiveLiteralsAvailableIndicator)?.let {
        it.showAvailablePopup()
      }
    }

    fun updateWidget(project: Project) {
      WindowManager.getInstance().getStatusBar(project)?.updateWidget(WIDGET_ID)
    }

    fun updateAllWidgets() {
      ProjectManager.getInstance().openProjects
        .forEach { updateWidget(it) }
    }
  }
}