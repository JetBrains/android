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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TASK_HOVER_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TASK_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.ICON_SIZE_PX
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TASK_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons
import com.android.tools.profilers.tasks.ProfilerTaskType
import main.utils.UnitConversion.toDpWithCurrentDisplayDensity
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskGridItem(task: ProfilerTaskType,
                 isSelectedTask: Boolean,
                 onTaskSelection: (task: ProfilerTaskType) -> Unit,
                 isTaskEnabled: Boolean) {
  // TODO (b/309506699) Give more description as to why it is not supported (because profileable, api level, etc.)
  val tooltipMessage = if (isTaskEnabled) "Tooltip for ${task.description}" else "${task.description} is not supported"

  val taskIconAndDescription: @Composable () -> Unit = {
    TaskIconAndDescriptionWrapper(task = task, isSelectedTask = isSelectedTask, isTaskEnabled = isTaskEnabled,
                                  onTaskSelection = onTaskSelection)
  }
  taskIconAndDescription.let { if (isTaskEnabled) it() else Tooltip(tooltip = { Text(tooltipMessage) }, content = it) }
}

@Composable
fun TaskIconAndDescriptionWrapper(task: ProfilerTaskType,
                                  isSelectedTask: Boolean,
                                  isTaskEnabled: Boolean,
                                  onTaskSelection: (task: ProfilerTaskType) -> Unit) {

  var isHovered by remember { mutableStateOf(false) }
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .width(TASK_WIDTH_DP)
      .clip(shape = RoundedCornerShape(5.dp))
      .background(
        if (isSelectedTask) {
          TASK_SELECTION_BACKGROUND_COLOR
        }
        else if (isHovered && isTaskEnabled) {
          TASK_HOVER_BACKGROUND_COLOR
        }
        else {
          Color.Transparent
        })
      .selectable(
        selected = isSelectedTask,
        enabled = isTaskEnabled
      ) {
        onTaskSelection(task)
      }
      .onHover { isHovered = it }
      .testTag("TaskGridItem")
  ) {
    TaskIconAndDescription(task = task, isTaskEnabled = isTaskEnabled, this)
  }
}

@Composable
fun TaskIconAndDescription(task: ProfilerTaskType, isTaskEnabled: Boolean, boxScope: BoxScope) {
  val iconSizeDp = ICON_SIZE_PX.toDpWithCurrentDisplayDensity()
  val taskIcon = if (isTaskEnabled) TaskBasedUxIcons.getTaskIcon(task) else TaskBasedUxIcons.DISABLED_TASK_ICON
  with(boxScope) {
    Column(
      modifier = Modifier.align(Alignment.Center).padding(20.dp)
    ) {
      Icon(
        resource = taskIcon.path,
        iconClass = taskIcon.iconClass,
        contentDescription = task.description,
        modifier = Modifier.size(iconSizeDp).align(Alignment.CenterHorizontally)
      )
      Text(
        text = task.description,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
      )
    }
  }
}