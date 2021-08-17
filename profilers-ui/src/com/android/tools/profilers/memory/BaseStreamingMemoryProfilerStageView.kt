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
package com.android.tools.profilers.memory

import com.android.tools.profilers.ProfilerColors
import com.android.tools.profilers.ProfilerFonts
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.event.LifecycleTooltip
import com.android.tools.profilers.event.LifecycleTooltipView
import com.android.tools.profilers.event.UserEventTooltip
import com.android.tools.profilers.event.UserEventTooltipView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI

abstract class BaseStreamingMemoryProfilerStageView<T: BaseStreamingMemoryProfilerStage>(profilersView: StudioProfilersView,
                                                                                         stage: T)
      : BaseMemoryProfilerStageView<T>(profilersView, stage) {

  val captureElapsedTimeLabel = JBLabel("").apply {
    font = ProfilerFonts.STANDARD_FONT
    border = JBUI.Borders.emptyLeft(5)
    foreground = ProfilerColors.CPU_CAPTURE_STATUS
  }

  init {
    // Turns on the auto-capture selection functionality - this will select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected.
    stage.enableSelectLatestCapture(true, ApplicationManager.getApplication()::invokeLater)
    tooltipBinder.apply {
      bind(MemoryUsageTooltip::class.java, ::MemoryUsageTooltipView)
      bind(LifecycleTooltip::class.java) { stageView: StageView<*>, tooltip -> LifecycleTooltipView(stageView.component, tooltip) }
      bind(UserEventTooltip::class.java) { stageView: StageView<*>, tooltip -> UserEventTooltipView(stageView.component, tooltip) }
    }
  }
}