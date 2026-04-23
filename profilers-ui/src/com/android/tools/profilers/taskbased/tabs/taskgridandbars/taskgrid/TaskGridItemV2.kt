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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TASK_HOVER_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TASK_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_HEIGHT_V2_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_TOOLTIP_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.icons.TaskIconUtils
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.intellij.openapi.editor.colors.EditorColorsManager
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ButtonState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.focusOutline

@Composable
fun TaskGridItemV2(task: ProfilerTaskType, isSelectedTask: Boolean, onTaskSelection: (task: ProfilerTaskType) -> Unit) {
  TaskIconAndDescriptionWrapperV2(task = task, isSelectedTask = isSelectedTask, onTaskSelection = onTaskSelection)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskIconAndDescriptionWrapperV2(task: ProfilerTaskType, isSelectedTask: Boolean, onTaskSelection: (task: ProfilerTaskType) -> Unit) {
  var isHovered by remember { mutableStateOf(false) }
  val interactionSource = remember { MutableInteractionSource() }
  var buttonState by remember(interactionSource) { mutableStateOf(ButtonState.of(enabled = true)) }

  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
        is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
      }
    }
  }

  Tooltip(
    { Text(TaskBasedUxStrings.getTaskTooltip(task, true), modifier = Modifier.width(TASK_TOOLTIP_WIDTH_DP)) },
    tooltipPlacement = TooltipPlacement.ComponentRect(),
  ) {
    Box(
      modifier =
        Modifier.padding(vertical = 5.dp)
          .fillMaxWidth()
          .fillMaxHeight()
          .heightIn(min = TASK_HEIGHT_V2_DP)
          .testTag("TaskGridItem")
          .focusOutline(buttonState, RoundedCornerShape(8.dp))
          .clip(shape = RoundedCornerShape(8.dp))
          .border(width = 1.dp, color = JewelTheme.globalColors.borders.normal, shape = RoundedCornerShape(8.dp))
          .background(
            if (isSelectedTask) {
              TASK_SELECTION_BACKGROUND_COLOR
            } else if (isHovered) {
              TASK_HOVER_BACKGROUND_COLOR
            } else {
              Color(EditorColorsManager.getInstance().globalScheme.defaultBackground.rgb)
            }
          )
          .selectable(selected = isSelectedTask, interactionSource = interactionSource, indication = null, role = Role.RadioButton) {
            onTaskSelection(task)
          }
          .onHover { isHovered = it }
    ) {
      val taskTitle = TaskBasedUxStrings.getTaskShortName(task, true)
      val taskDescription = TaskBasedUxStrings.getTaskDescriptions(task)

      Column(modifier = Modifier.fillMaxWidth().padding(16.dp).testTag(task.description)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("TaskGridItemV2")) {
          Icon(TaskIconUtils.getTaskIconKey(task), contentDescription = task.description, modifier = Modifier.size(28.dp))
          Spacer(modifier = Modifier.width(10.dp))
          Text(text = taskTitle, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = taskDescription)
      }
    }
  }
}
