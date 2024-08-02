/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.analytics.PreviewCanvasTracker
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.GALLERY_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI

/** [DropDownAction] that allows switching the layout manager in the surface. */
class SwitchSurfaceLayoutManagerAction(
  layoutManagers: List<SurfaceLayoutOption>,
  private val isActionEnabled: (AnActionEvent) -> Boolean = { true },
) : DropDownAction("Switch Layout", "Changes the layout of the preview elements.", null) {

  private val enabledIcon = AllIcons.Debugger.RestoreLayout
  private val disabledIcon = IconLoader.getDisabledIcon(AllIcons.Debugger.RestoreLayout)

  inner class SetSurfaceLayoutManagerAction(private val option: SurfaceLayoutOption) :
    ToggleAction(option.displayName) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        updateMode(e.dataContext)
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val manager = e.dataContext.findPreviewManager(PreviewModeManager.KEY) ?: return false
      return manager.mode.value.layoutOption == option
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = isActionEnabled(e)
    }

    private fun updateMode(dataContext: DataContext) {
      PreviewCanvasTracker.getInstance().logSwitchLayout(option.layoutManager)
      val manager = dataContext.findPreviewManager(PreviewModeManager.KEY) ?: return

      if (option == GALLERY_LAYOUT_OPTION) {
        // If turning on Gallery layout option - it should be set in preview.
        // TODO (b/292057010) If group filtering is enabled - first element in this group
        // should be selected.
        val element =
          dataContext
            .findPreviewManager(PreviewFlowManager.KEY)
            ?.allPreviewElementsFlow
            ?.value
            ?.asCollection()
            ?.firstOrNull()
        manager.setMode(PreviewMode.Gallery(element))
      } else if (manager.mode.value is PreviewMode.Gallery) {
        // When switching from Gallery mode to Default layout mode - need to set back
        // Default preview mode.
        manager.setMode(PreviewMode.Default(option))
      } else {
        manager.setMode(manager.mode.value.deriveWithLayout(option))
      }
    }
  }

  init {
    templatePresentation.isHideGroupIfEmpty = true

    // We will only add the actions and be visible if there are more than one option
    if (layoutManagers.size > 1) {
      layoutManagers.forEach { add(SetSurfaceLayoutManagerAction(it)) }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place).apply {
      border = JBUI.Borders.empty(1, 2)
    }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val shouldEnableAction = isActionEnabled(e)
    e.presentation.isEnabled = shouldEnableAction
    // Since this is an ActionGroup, IntelliJ will set the button icon to enabled even though it is
    // disabled. Only when clicking on the
    // button the icon will be disabled (and gets re-enabled when releasing the mouse), since the
    // action itself is disabled and not popup
    // will show up. Since we want users to know immediately that this action is disabled, we
    // explicitly set the icon style when the
    // action is disabled.
    e.presentation.icon = if (shouldEnableAction) enabledIcon else disabledIcon
  }
}
