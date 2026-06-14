/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.tracer

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

class ToggleCompositionTracingAction : ToggleAction("Enable Compose Composition Tracing"), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    return PropertiesComponent.getInstance().getBoolean(COMPOSITION_TRACING_ENABLED_KEY, false)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().setValue(COMPOSITION_TRACING_ENABLED_KEY, state, false)
    AndroidxTracerService.getInstance().setCompositionTracingEnabled(state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val isTracingLibraryEnabled = StudioFlags.STUDIO_TRACE_LIBRARY_ENABLED.get()
    val isTracingEnabled = PropertiesComponent.getInstance().getBoolean(TRACING_ENABLED_KEY, false)
    e.presentation.isVisible = isTracingLibraryEnabled
    e.presentation.isEnabled = isTracingEnabled
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

const val COMPOSITION_TRACING_ENABLED_KEY = "android.perfetto.composition.tracing.enabled"
