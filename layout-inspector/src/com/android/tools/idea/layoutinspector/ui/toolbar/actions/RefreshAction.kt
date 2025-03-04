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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.application.ApplicationManager
import icons.StudioIcons

/** Action to refresh the content of the inspector. */
object RefreshAction :
  AnAction({ "Refresh Layout" }, StudioIcons.LayoutEditor.Toolbar.REFRESH),
  TooltipDescriptionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val inspector = LayoutInspector.get(event) ?: return
    ApplicationManager.getApplication().executeOnPooledThread { inspector.currentClient.refresh() }
    inspector.currentClient.stats.refreshButtonClicked()
  }

  override fun update(event: AnActionEvent) {
    val currentClient = LayoutInspector.get(event)?.currentClient
    event.presentation.isEnabled = currentClient?.isConnected == true && !currentClient.inLiveMode
    event.presentation.description =
      "When live updates are disabled, click to manually refresh the layout information and images."
  }
}
