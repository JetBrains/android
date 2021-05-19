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

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.common.actions.isActionEventFromJTextField
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to change the current Scene View in the Design Surface to the next option as defined in [NlScreenViewProvider].
 */
class SwitchToNextScreenViewProviderAction : AnAction() {
  companion object {
    @JvmStatic
    fun getInstance(): SwitchToNextScreenViewProviderAction {
      return ActionManager.getInstance().getAction(DesignerActions.ACTION_SWITCH_DESIGN_MODE) as SwitchToNextScreenViewProviderAction
    }
  }

  override fun update(e: AnActionEvent) {
    if (isActionEventFromJTextField(e)) {
      e.presentation.isEnabled = false
      return
    }
    e.presentation.isEnabled = e.getData(NlActionManager.LAYOUT_EDITOR) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(NlActionManager.LAYOUT_EDITOR)
    surface.screenViewProvider.next()?.let {
      surface.setScreenViewProvider(it, true)
    }
  }
}