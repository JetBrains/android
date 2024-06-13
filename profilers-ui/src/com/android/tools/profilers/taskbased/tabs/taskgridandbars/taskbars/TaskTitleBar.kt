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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TOP_BAR_END_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TOP_BAR_HEIGHT_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TOP_BAR_START_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.TOP_BAR_TITLE
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text

/**
 * The bar hosted above the task grid containing the title of the task grid ("Tasks"). Optionally includes a task config icon button,
 * that, on click, opens the task config dialog.
 */
@Composable
private fun TopBarContainer(taskConfigIconButton: @Composable () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TOP_BAR_HEIGHT_DP)
      .padding(TOP_BAR_START_PADDING_DP, end = TOP_BAR_END_PADDING_DP),
    verticalAlignment = Alignment.CenterVertically) {
    Text(TOP_BAR_TITLE)
    Spacer(modifier = Modifier.weight(1f))
    taskConfigIconButton()
  }
}

@Composable
fun TopBar(profilers: StudioProfilers, ideProfilerComponents: IdeProfilerComponents) {
  TopBarContainer {
    IconButton(
      onClick = {
        val ideServices = profilers.ideServices
        val model = CpuProfilerConfigModel(profilers, CpuProfilerStage(profilers))
        ideProfilerComponents.openTaskConfigurationsDialog(model, ideServices)
      }) {
      Icon(
        painter = StudioIconsCompose.Common.Settings().getPainter().value,
        contentDescription = TaskBasedUxStrings.TASK_CONFIG_DIALOG_DESC,
        modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP)
      )
    }
  }
}

@Composable
fun TopBar() {
  TopBarContainer {}
}