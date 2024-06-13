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

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TASK_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TASK_HOVER_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.icons.TaskIconUtils
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings
import com.android.tools.profilers.tasks.ProfilerTaskType
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.ui.component.ButtonState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.focusOutline

@Composable
fun TaskGridItem(task: ProfilerTaskType, isSelectedTask: Boolean, onTaskSelection: (task: ProfilerTaskType) -> Unit) {
  TaskIconAndDescriptionWrapper(task = task, isSelectedTask = isSelectedTask, onTaskSelection = onTaskSelection)
}

@Composable
fun TaskIconAndDescriptionWrapper(task: ProfilerTaskType, isSelectedTask: Boolean, onTaskSelection: (task: ProfilerTaskType) -> Unit) {

  var isHovered by remember { mutableStateOf(false) }

  val interactionSource = remember { MutableInteractionSource() }
  var buttonState by remember(interactionSource) {
    mutableStateOf(ButtonState.of(enabled = true))
  }

  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is FocusInteraction.Focus -> buttonState = buttonState.copy(focused = true)
        is FocusInteraction.Unfocus -> buttonState = buttonState.copy(focused = false)
      }
    }
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .padding(vertical = 5.dp)
      .fillMaxWidth()
      .focusOutline(buttonState, RoundedCornerShape(2.dp))
      .clip(shape = RoundedCornerShape(4.dp))
      .background(
        if (isSelectedTask) {
          TASK_SELECTION_BACKGROUND_COLOR
        }
        else if (isHovered) {
          TASK_HOVER_BACKGROUND_COLOR
        }
        else {
          Color.Transparent
        })
      .selectable(selected = isSelectedTask, interactionSource = interactionSource, indication = null) {
        onTaskSelection(task)
      }
      .onHover { isHovered = it }
      .testTag("TaskGridItem")
  ) {
    TaskIconAndDescription(task = task, this)
  }
}

@Composable
fun TaskIconAndDescription(task: ProfilerTaskType, boxScope: BoxScope) {
  val taskTitle = TaskBasedUxStrings.getTaskTitle(task)
  val taskSubtitle = TaskBasedUxStrings.getTaskSubtitle(task)
  with(boxScope) {
    Column(
      modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(vertical = 20.dp, horizontal = 10.dp).testTag(task.description)
    ) {
      Icon(
        painter = TaskIconUtils.getLargeTaskIconPainter(task),
        contentDescription = task.description,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = taskTitle,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
      Spacer(modifier = Modifier.height(5.dp))
      taskSubtitle?.let {
        Text(
          text = it,
          textAlign = TextAlign.Center,
          color = Color.Gray,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )
      }
    }
  }
}