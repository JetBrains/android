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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * This file contains exploratory actions meant for a user study.
 */

object CallstackAction : ToggleAction("Show compose as Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.composeAsCallstack

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.composeAsCallstack = state

    // Update the current client if currently connected:
    val client = inspector(event)?.currentClient ?: return
    if (client.isConnected) {
      if (client.isCapturing) {
        client.startFetching()
      } else {
        client.refresh()
      }
    }
  }
}

object DrawablesInCallstackAction : ToggleAction("Show compose Drawables in Callstack", null, null) {

  override fun isSelected(event: AnActionEvent): Boolean =
    TreeSettings.composeDrawablesInCallstack

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    TreeSettings.composeDrawablesInCallstack = state

    // Update the current client if currently connected:
    val client = inspector(event)?.currentClient ?: return
    if (client.isConnected) {
      if (client.isCapturing) {
        client.startFetching()
      } else {
        client.refresh()
      }
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = TreeSettings.composeAsCallstack
  }
}

private fun inspector(event: AnActionEvent): LayoutInspector? =
  event.getData(LAYOUT_INSPECTOR_DATA_KEY)
