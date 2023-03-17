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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.common.surface.DesignSurface.SceneViewAlignment
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI

/**
 * Interface to be used by components that can switch [SurfaceLayoutManager]s.
 */
interface LayoutManagerSwitcher {
  /**
   * Returns true if the current selected [SurfaceLayoutManager] is [layoutManager].
   */
  fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager): Boolean

  /**
   * Sets a new [SurfaceLayoutManager].
   */
  fun setLayoutManager(layoutManager: SurfaceLayoutManager, sceneViewAlignment: SceneViewAlignment = SceneViewAlignment.CENTER)
}

/**
 * Wrapper class to define the options available for [SwitchSurfaceLayoutManagerAction].
 * @param displayName Name to be shown for this option.
 * @param layoutManager [SurfaceLayoutManager] to switch to when this option is selected.
 */
data class SurfaceLayoutManagerOption(val displayName: String,
                                      val layoutManager: SurfaceLayoutManager,
                                      val sceneViewAlignment: SceneViewAlignment = SceneViewAlignment.CENTER)

/**
 * [DropDownAction] that allows switching the layout manager in the surface.
 */
class SwitchSurfaceLayoutManagerAction(private val layoutManagerSwitcher: LayoutManagerSwitcher,
                                       layoutManagers: List<SurfaceLayoutManagerOption>,
                                       private val isActionEnabled: (AnActionEvent) -> Boolean = { true },
                                       private val onLayoutSelected: (SurfaceLayoutManagerOption) -> Unit
) : DropDownAction(
  "Switch Layout",
  "Changes the layout of the preview elements.",
  null) {

  /**
   * When using [AllIcons.Debugger.RestoreLayout] as the icon, this action is considered as a multi-choice group, even
   * [Presentation.setMultiChoice] sets to false. We clone the icon here so we can control the multi-choice state of this action ourselves.
   *
   * @see com.intellij.openapi.actionSystem.impl.Utils.isMultiChoiceGroup
   */
  private val enabledIcon = IconLoader.copy(AllIcons.Debugger.RestoreLayout, null, true)
  private val disabledIcon = IconLoader.getDisabledIcon(AllIcons.Debugger.RestoreLayout)

  inner class SetSurfaceLayoutManagerAction(private val option: SurfaceLayoutManagerOption) : ToggleAction(option.displayName) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      layoutManagerSwitcher.setLayoutManager(option.layoutManager, option.sceneViewAlignment)
      if (state) {
        onLayoutSelected(option)
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = layoutManagerSwitcher.isLayoutManagerSelected(option.layoutManager)
  }

  init {
    templatePresentation.isHideGroupIfEmpty = true

    // We will only add the actions and be visible if there are more than one option
    if (layoutManagers.size > 1) {
      layoutManagers.forEach { add(SetSurfaceLayoutManagerAction(it)) }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place).apply { border = JBUI.Borders.empty(1, 2) }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val shouldEnableAction = isActionEnabled(e)
    e.presentation.isEnabled = shouldEnableAction
    // Since this is an ActionGroup, IntelliJ will set the button icon to enabled even though it is disabled. Only when clicking on the
    // button the icon will be disabled (and gets re-enabled when releasing the mouse), since the action itself is disabled and not popup
    // will show up. Since we want users to know immediately that this action is disabled, we explicitly set the icon style when the
    // action is disabled.
    e.presentation.icon = if (shouldEnableAction) enabledIcon else disabledIcon
  }
}