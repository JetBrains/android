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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.compose.preview.PreviewPowerSaveManager
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.editors.literals.FasterPreviewApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.util.ui.JBUI
import java.awt.Dimension

/**
 * [AnAction] that enables/disables the fast preview in the project.
 */
private class ToggleFastPreviewAction: AnAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val presentation = e.presentation
    val project = e.project
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }

    if (PreviewPowerSaveManager.isInPowerSaveMode) {
      presentation.description = message("action.preview.fast.refresh.disabled.in.power.save.description")
      presentation.isEnabled = false
    }
    else {
      presentation.description = message("action.preview.fast.refresh.toggle.description")
      presentation.isEnabled = true
    }

    presentation.text = if (PreviewLiveEditManager.getInstance(project).isEnabled)
      message("action.preview.fast.refresh.disable.title")
    else
      message("action.preview.fast.refresh.enable.title")
  }

  override fun actionPerformed(e: AnActionEvent) {
    FasterPreviewApplicationConfiguration.getInstance().isEnabled = !FasterPreviewApplicationConfiguration.getInstance().isEnabled
  }
}

/**
 * A customized [DropDownAction] with no text and only displaying the down arrow with the [ToggleFastPreviewAction].
 */
private class FastPreviewDropDownAction: DropDownAction(null, null, AllIcons.General.ArrowDownSmall) {
  init {
    // Create a thin arrow button by making the button a bit narrower and disable the default drop down icon.
    setMinimumButtonSize(Dimension(JBUI.scale(19), ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height))
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)

    add(ToggleFastPreviewAction())
  }
}

/**
 * Additional actions for fast preview. This group is meant to be displayed next to the refresh button to indicate "related actions".
 * The group will add a down arrow with the popup menu actions for fast preview and a separator: `▼ |`
 */
class FastPreviewAdditionalActionsGroup: DefaultActionGroup(FastPreviewDropDownAction(), Separator.create()) {
  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW.get()
  }
}