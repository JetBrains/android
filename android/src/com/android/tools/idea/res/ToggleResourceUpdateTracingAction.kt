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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.DumbAwareToggleAction

/**
 * Enables/disables tracing in [ResourceFolderRepository].
 */
class ToggleResourceUpdateTracingAction : DumbAwareToggleAction("Enable Resource Update Tracing") {
  override fun isSelected(event: AnActionEvent): Boolean = ResourceFolderRepository.isTracingActive()

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (state) {
      ResourceFolderRepository.startTracing()
    }
    else {
      ResourceFolderRepository.stopTracingAndDump()
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isVisible = StudioFlags.RESOURCE_REPOSITORY_TRACE_UPDATES.get() || ApplicationInfoEx.getInstanceEx().isEAP
  }
}