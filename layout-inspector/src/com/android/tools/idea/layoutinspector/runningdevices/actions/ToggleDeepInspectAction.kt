/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import icons.StudioIcons

/** Action used to toggle Deep Inspect on and off. */
class ToggleDeepInspectAction(
  @UiThread private val isSelected: () -> Boolean,
  @UiThread private val setSelected: (Boolean) -> Unit,
  @UiThread private val connectedClientProvider: () -> InspectorClient,
) :
  ToggleAction(
    { LayoutInspectorBundle.message("toggle.deep.inspect") },
    StudioIcons.Compose.Toolbar.INSPECT_PREVIEW,
  ),
  TooltipDescriptionProvider {
  override fun isSelected(e: AnActionEvent) = isSelected()

  override fun setSelected(e: AnActionEvent, state: Boolean) = setSelected(state)

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.description = LayoutInspectorBundle.message("deep.inspect.description")

    val currentClient = connectedClientProvider()
    event.presentation.isEnabled = currentClient.isConnected
  }
}
