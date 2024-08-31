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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.dividers.ToolWindowHorizontalDivider
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.actionbars.LeakCanaryActionBar
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails.LeakDetailsPanel
import com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist.LeakListView
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout

@Composable
fun LeakCanaryScreen(leakCanaryModel: LeakCanaryModel) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LeakCanaryActionBar(leakCanaryModel)
    ToolWindowHorizontalDivider()
    HorizontalSplitLayout(
      minRatio = TaskBasedUxDimensions.SELECTION_PANEL_MIN_RATIO_FLOAT,
      maxRatio = TaskBasedUxDimensions.SELECTION_PANEL_MAX_RATIO_FLOAT,
      first = {
        LeakListView(leakCanaryModel, it)
      },
      second = {
        LeakDetailsColumn(it, leakCanaryModel)
      }
    )
  }
}

@Composable
private fun LeakDetailsColumn(modifier: Modifier, leakCanaryModel: LeakCanaryModel) {
  val selectedLeak by leakCanaryModel.selectedLeak.collectAsState()
  Column(modifier = modifier) {
    LeakDetailsPanel(selectedLeak = selectedLeak, gotoDeclaration = leakCanaryModel::goToDeclaration)
  }
}