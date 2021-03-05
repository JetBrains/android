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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons

/**
 * Filter system nodes from view hierarchy and compose hierarchy.
 */
object FilterNodeAction : ToggleAction("Filter system-defined layers", null, StudioIcons.Common.FILTER) {
  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.hideSystemNodes

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.hideSystemNodes = state

    // Update the current client if currently connected:
    val client = LayoutInspector.get(event)?.currentClient ?: return
    if (client.isConnected) {
      if (client.isCapturing) {
        client.startFetching()
      } else {
        client.refresh()
      }
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isVisible =
      LayoutInspector.get(event)?.currentClient?.let { client ->
        !client.isConnected // If not running, default to visible so user can modify selection when next client is connected
        || client.capabilities.contains(Capability.SUPPORTS_FILTERING_SYSTEM_NODES)
      }
      ?: true
  }
}
