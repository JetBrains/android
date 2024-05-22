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
package com.android.tools.idea.res

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** Enables/disables resource update trace. */
class ToggleResourceTraceAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean = ResourceUpdateTracer.isTracingActive

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      ResourceUpdateTracer.startTracing()
    } else {
      ResourceUpdateTracer.stopTracing()
    }
    ResourceUpdateTraceSettings.getInstance().enabled = state
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
