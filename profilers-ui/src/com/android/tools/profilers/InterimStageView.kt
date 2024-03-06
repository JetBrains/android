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
package com.android.tools.profilers

import androidx.compose.ui.awt.ComposePanel
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.taskbased.task.interim.RecordingScreenModel
import com.android.tools.profilers.taskbased.tabs.task.interim.RecordingScreen
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.awt.BorderLayout
import javax.swing.JPanel

@OptIn(ExperimentalJewelApi::class)
class InterimStageView<T>(profilersView: StudioProfilersView, stage: T) : StageView<T>(profilersView,
                                                                                       stage) where T : StreamingStage, T : InterimStage {
  private val recordingScreenModel: RecordingScreenModel<*>
    // If Task-Based UX is enabled, then stage.recordingScreenModel will be instantiated with a non-null value. This class is only ever
    // used when the flag is enabled, so a non-null assertion (!!) can be made.
    get() = stage.recordingScreenModel!!

  init {
    // Turns on the auto-capture selection functionality - this will select the latest user-triggered heap dump/allocation tracking
    // capture object if an existing one has not been selected. This needs to explicitly enabled for the memory tasks utilizing the
    // MainMemoryProfilerStage to facilitate the recording of the capture, while for tasks using CpuProfilerStage, it is automatically done.
    if (stage is MainMemoryProfilerStage) {
      stage.enableSelectLatestCapture(true, ApplicationManager.getApplication()::invokeLater)
    }

    enableNewSwingCompositing()
    val composePanel = ComposePanel()
    composePanel.setContent {
      SwingBridgeTheme {
        RecordingScreen(recordingScreenModel)
      }
    }
    component.add(composePanel, BorderLayout.CENTER)
  }

  override fun getToolbar() = JPanel()

  override fun isToolbarVisible() = false
}