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
package com.android.tools.profilers.taskbased.tabs.home.processlist

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProcessUtils.isProfileable
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TABLE_HEADER_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TABLE_SEPARATOR_COLOR
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TABLE_ROW_HEIGHT_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.table.LeftAlignedColumnText
import com.android.tools.profilers.taskbased.common.table.RightAlignedColumnText
import icons.StudioIconsCompose
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
private fun ProcessListRow(selectedProcess: Common.Process, process: Common.Process, isPreferredProcess: Boolean) {
  val processName = process.name
  val pid = process.pid

  if (process == Common.Process.getDefaultInstance()) {
    return
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TABLE_ROW_HEIGHT_DP)
      .background(
        if (process == selectedProcess)
          TABLE_ROW_SELECTION_BACKGROUND_COLOR
        else
          Color.Transparent
      )
      .padding(horizontal = TABLE_ROW_HORIZONTAL_PADDING_DP)
      .testTag("ProcessListRow")
  ) {

    val isRunning = process.state == Common.Process.State.ALIVE
    val pidText = if (isRunning) pid.toString() else ""
    val configurationText =
      if (isRunning)
        (if (process.isProfileable()) TaskBasedUxStrings.PROFILEABLE_PROCESS_TITLE else TaskBasedUxStrings.DEBUGGABLE_PROCESS_TITLE)
      else TaskBasedUxStrings.DEAD_PROCESS_TITLE
    // The android head icon to indicate the preferred process
    if (isPreferredProcess) {
      val androidHeadIconPainter by StudioIconsCompose.Common.AndroidHead().getPainter()
      LeftAlignedColumnText(processName, androidHeadIconPainter, rowScope = this)
    }
    else {
      LeftAlignedColumnText(processName, rowScope = this)
    }

    RightAlignedColumnText(text = pidText, colWidth = TaskBasedUxDimensions.PID_COL_WIDTH_DP)
    RightAlignedColumnText(text = configurationText, colWidth = TaskBasedUxDimensions.MANIFEST_CONFIG_COL_WIDTH_DP)
  }
}

@Composable
private fun ProcessListHeader() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TABLE_ROW_HEIGHT_DP)
      .background(TABLE_HEADER_BACKGROUND_COLOR)
      .padding(horizontal = TABLE_ROW_HORIZONTAL_PADDING_DP)
  ) {
    LeftAlignedColumnText(text = "Process name", rowScope = this)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = "PID", colWidth = TaskBasedUxDimensions.PID_COL_WIDTH_DP)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = "Manifest Configuration", colWidth = TaskBasedUxDimensions.MANIFEST_CONFIG_COL_WIDTH_DP)
  }
}

@Composable
fun ProcessTable(processList: List<Common.Process>,
                 selectedProcess: Common.Process,
                 preferredProcessName: String?,
                 onProcessSelection: (Common.Process) -> Unit) {
  val listState = rememberSelectableLazyListState()

  Box(modifier = Modifier.fillMaxSize()) {
    SelectableLazyColumn (
      state = listState,
      selectionMode = SelectionMode.Single,
      onSelectedIndexesChanged = {
        // The - 1 is to account for the sticky header.
        if (it.isNotEmpty() && processList[it.first() - 1] != selectedProcess) {
          val newSelectedDeviceProcess = processList[it.first() - 1]
          onProcessSelection(newSelectedDeviceProcess)
        }
      }
    ) {
      stickyHeader(key = Integer.MAX_VALUE) {
        ProcessListHeader()
        Divider(color = TABLE_SEPARATOR_COLOR, modifier = Modifier.fillMaxWidth(), thickness = 1.dp, orientation = Orientation.Horizontal)
      }
      items(items = processList, key = { it }) { process ->
        ProcessListRow(selectedProcess = selectedProcess, process = process, isPreferredProcess = (process.name == preferredProcessName))
      }
    }

    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(listState.lazyListState),
      modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
    )
  }
}
