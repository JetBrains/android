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
package com.android.tools.idea.editors.literals.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.editors.literals.LiveLiteralsService
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.editors.literals.ui.LiveLiteralsConfigurable
import com.android.tools.idea.projectsystem.cacheInvalidatingOnSyncModifications
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GotItTooltip
import com.intellij.ui.LayeredIcon
import com.intellij.util.IconUtil
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.awt.Point
import java.net.URL
import javax.swing.Icon
import javax.swing.JComponent

private val AVAILABLE_ICON = StudioIcons.Shell.StatusBar.LIVE_LITERALS
private val NOT_AVAILABLE_ICON = IconUtil.desaturate(AVAILABLE_ICON)
private val ERROR_ICON = LayeredIcon(StudioIcons.Shell.StatusBar.LIVE_LITERALS, AllIcons.Nodes.ErrorMark)

private fun Project.isCompose() = cacheInvalidatingOnSyncModifications {
  allModules().any { it.getModuleSystem().usesCompose }
}

/**
 * Shows the first time popup that describes the feature.
 */
private fun showFirstTimeAvailablePopup(component: JComponent, parentDisposable: Disposable) {
  val highlightAction = ActionManager.getInstance().getAction("Compose.Live.Literals.ToggleHighlight")
  val shortcutLabel = highlightAction.shortcutSet.shortcuts.firstOrNull()?.let {
    " (${KeymapUtil.getShortcutText(it)})"
  } ?: ""
  val toggleLiteralsActionName = "\"${highlightAction.templateText}\"$shortcutLabel"
  GotItTooltip("android.live.literals.popup", AndroidBundle.message("live.literals.is.available", toggleLiteralsActionName),
               parentDisposable)
    .withLink(AndroidBundle.message("live.literals.is.available.disable.hint")) {
      ShowSettingsUtil.getInstance().showSettingsDialog(null, LiveLiteralsConfigurable::class.java)
    }
    .withShowCount(1)
    // Show pointing to the bottom of the button
    .show(component) { Point(component.width / 2, component.height) }
}

/**
 * Action that shows the status of Live Literals and allows accessing some of the options and actions.
 */
class LiveLiteralsStatusAction(private val project: Project) : DropDownAction(null, null, null) {
  /**
   * Will be true once this action detects Live Literals being enabled the first time. This is just to avoid calling [GotItTooltip]
   * unnecessarily and removing this flag should have no visible effect in the user flow.
   */
  private var firstTimeEnableDetected = false
  private val liveLiteralsService by lazy { LiveLiteralsService.getInstance(project) }
  private val liveLiteralsDeploymentReportService by lazy { LiveLiteralsDeploymentReportService.getInstance(project) }

  /**
   * Cached [JComponent] for when the action is initialized.
   */
  private var cachedButtonComponent: JComponent? = null

  override fun displayTextInToolbar(): Boolean = true

  private fun getIconAndTextForCurrentState(): Pair<String, Icon?> {
    return when {
      // No device has ever reported having literals
      !project.isCompose() || !liveLiteralsService.hasEverBeenActive -> AndroidBundle.message("live.literals.is.disabled") to null
      !liveLiteralsService.isAvailable -> AndroidBundle.message("live.literals.is.disabled") to NOT_AVAILABLE_ICON
      liveLiteralsDeploymentReportService.hasProblems -> AndroidBundle.message("live.literals.is.enabled") to ERROR_ICON
      else -> AndroidBundle.message("live.literals.is.enabled") to AVAILABLE_ICON
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent = object : ActionButtonWithText(this, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun updateToolTipText() {
        if (Registry.`is`("ide.helptooltip.enabled")) {
          HelpTooltip.dispose(this)
          val state = getIconAndTextForCurrentState()
          val title = state.first

          HelpTooltip.dispose(this)
          HelpTooltip()
            .setTitle(title)
            .setDescription(AndroidBundle.message("live.literals.tooltip.description"))
            .setBrowserLink(AndroidBundle.message("live.literals.tooltip.url.label"),
                            URL("https://developer.android.com/jetpack/compose/tooling#live-literals"))
            .installOn(this)
        }
        else {
          super.updateToolTipText()
        }
      }
    }.also {
      cachedButtonComponent = it
    }

  override fun updateActions(context: DataContext): Boolean {
    removeAll()
    add(DefaultActionGroup(
      ToggleLiveLiteralsStatusAction(),
      ToggleLiveLiteralsHighlightAction(),
      ShowLiveLiteralsProblemAction(),
      UpdateHighlightsKeymapAction(),
      CustomizeLiveLiteralsThemeAction()
    ))

    return false
  }

  override fun update(e: AnActionEvent) {
    with(e.presentation) {
      val (text, icon) = getIconAndTextForCurrentState()
      isEnabledAndVisible = icon != null
      val currentButtomComponent = cachedButtonComponent
      if (currentButtomComponent != null && isEnabledAndVisible && !firstTimeEnableDetected) {
        firstTimeEnableDetected = true
        showFirstTimeAvailablePopup(currentButtomComponent, liveLiteralsService)
      }
      this.text = text
      this.icon = icon
    }
  }
}