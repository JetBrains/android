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

import com.android.tools.adtui.common.AdtUiUtils.showAbove
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.literals.actions.CustomizeLiveLiteralsThemeAction
import com.android.tools.idea.editors.literals.actions.ShowLiveLiteralsProblemAction
import com.android.tools.idea.editors.literals.actions.ToggleLiveLiteralsHighlightAction
import com.android.tools.idea.editors.literals.actions.ToggleLiveLiteralsStatusAction
import com.android.tools.idea.editors.literals.actions.UpdateHighlightsKeymapAction
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.application.subscribe
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ClickListener
import com.intellij.ui.GotItTooltip
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

private val AVAILABLE_ICON = StudioIcons.Shell.StatusBar.LIVE_LITERALS
private val NOT_AVAILABLE_ICON = IconUtil.desaturate(AVAILABLE_ICON)
private val ERROR_ICON = LayeredIcon(StudioIcons.Shell.StatusBar.LIVE_LITERALS, AllIcons.Nodes.ErrorMark)
private const val WIDGET_ID = "LiveLiteralsWidget"

private class LiveLiteralsAvailableIndicator(private val project: Project) :
  CustomStatusBarWidget, StatusBarWidget.Multiframe, JLabel() {
  private val literalsService = LiveLiteralsService.getInstance(project)
  private val deployReportingService = LiveLiteralsDeploymentReportService.getInstance(project)
  private var statusBar: StatusBar? = null

  init {
    isVisible = false
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        onClick()
        return true
      }
    }.installOn(this, false)
    deployReportingService.subscribe(this, object : LiveLiteralsDeploymentReportService.Listener {
      override fun onMonitorStarted(deviceId: String) {
        LiveLiteralsAvailableIndicatorFactory.indicatorActivated()
        LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
      }

      override fun onMonitorStopped(deviceId: String) = LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
      override fun onLiveLiteralsPushed(deviceId: String) = LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
    })
    DumbService.DUMB_MODE.subscribe(this, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        if (project.isDisposed) return
        LiveLiteralsAvailableIndicatorFactory.updateWidget(project)
      }
    })
  }

  private fun isComposeProject() = project.cacheInvalidatingOnRootModifications {
    project.allModules().any { it.getModuleSystem().usesCompose }
  }

  override fun ID(): String = WIDGET_ID
  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
  }

  override fun getComponent(): JComponent = this

  override fun copy(): StatusBarWidget = LiveLiteralsAvailableIndicator(project)

  private fun onClick() {
    if (!isComposeProject()) {
      Logger.getInstance(LiveLiteralsAvailableIndicator::class.java).warn("Not a Compose Project")
      return
    }

    JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                        DefaultActionGroup(
                                                          ToggleLiveLiteralsStatusAction(),
                                                          ToggleLiveLiteralsHighlightAction(),
                                                          ShowLiveLiteralsProblemAction(),
                                                          UpdateHighlightsKeymapAction(),
                                                          CustomizeLiveLiteralsThemeAction()
                                                        ),
                                                        DataManager.getInstance().getDataContext(component),
                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                        true).showAbove(component)
  }

  private fun getIconAndTextForCurrentState(): Pair<String, Icon?> = when {
    // No device has ever reported having literals
    !isComposeProject() || !LiveLiteralsAvailableIndicatorFactory.hasEverBeenActive -> message("live.literals.is.disabled") to null
    !literalsService.isAvailable -> message("live.literals.is.disabled") to NOT_AVAILABLE_ICON
    deployReportingService.hasProblems -> message("live.literals.is.enabled") to ERROR_ICON
    else -> message("live.literals.is.enabled") to AVAILABLE_ICON
  }

  override fun getToolTipText(): String = getIconAndTextForCurrentState().first

  fun updateWidget() {
    icon = getIconAndTextForCurrentState().second
    isVisible = icon != null
    revalidate()
    repaint()
  }

  fun showAvailablePopup(invokedByUser: Boolean = false) {
    val highlightAction = ActionManager.getInstance().getAction("Compose.Live.Literals.ToggleHighlight")
    val shortcutLabel = highlightAction.shortcutSet.shortcuts.firstOrNull()?.let {
      " (${KeymapUtil.getShortcutText(it)})"
    } ?: ""
    val toggleLiteralsActionName = "\"${highlightAction.templateText}\"$shortcutLabel"
    GotItTooltip("android.live.literals.popup", message("live.literals.is.available", toggleLiteralsActionName), this)
      .withLink(message("live.literals.is.available.disable.hint")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, LiveLiteralsConfigurable::class.java)
      }
      .withShowCount(if (invokedByUser) Int.MAX_VALUE else 1)
      .show(this) { Point(0, 0) }
  }

  override fun dispose() {
    statusBar = null
  }
}

class LiveLiteralsAvailableIndicatorFactory : StatusBarWidgetFactory {
  override fun getId(): String = "LiveLiteralsAvailableWidget"
  override fun getDisplayName(): String = message("live.literals.tracking.display.name")
  override fun isAvailable(project: Project): Boolean = LiveLiteralsService.getInstance(project).isEnabled
  override fun createWidget(project: Project): StatusBarWidget = LiveLiteralsAvailableIndicator(project).also {
    DumbService.getInstance(project).runWhenSmart { it.updateWidget() }
  }
  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }
  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

  companion object {
    /** Will be set to true the first time an indicator created by this factory receives a notification from a device being available. */
    var hasEverBeenActive = false
      private set

    private fun findLiveLiteralsIndicator(project: Project): LiveLiteralsAvailableIndicator? =
      WindowManager.getInstance()?.getStatusBar(project)?.getWidget(WIDGET_ID) as? LiveLiteralsAvailableIndicator

    fun showIsAvailablePopup(project: Project) {
      findLiveLiteralsIndicator(project)?.showAvailablePopup()
    }

    fun updateWidget(project: Project) {
      findLiveLiteralsIndicator(project)?.updateWidget()
    }

    fun updateAllWidgets() {
      ProjectManager.getInstance().openProjects
        .forEach { updateWidget(it) }
    }

    fun indicatorActivated() {
      hasEverBeenActive = true
    }
  }
}