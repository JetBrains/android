/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary

import androidx.compose.ui.awt.ComposePanel
import com.android.tools.adtui.compose.StudioTheme
import com.android.tools.profilers.StageView
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.awt.BorderLayout
import javax.swing.JPanel

@OptIn(ExperimentalJewelApi::class)
class LeakCanaryTaskView(profilersView: StudioProfilersView,
                         model: LeakCanaryModel) : StageView<LeakCanaryModel>(profilersView, model) {
  init {
    enableNewSwingCompositing()
    val composePanel = ComposePanel()
    composePanel.setContent {
      StudioTheme {
        LeakCanaryScreen(model)
      }
    }
    component.add(composePanel, BorderLayout.CENTER)
  }

  override fun getToolbar() = JPanel()
  override fun isToolbarVisible() = false
}