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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.ProcessUtils.isProfileable
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TABLE_HEADER_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TABLE_ROW_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxColors.TABLE_SEPARATOR_COLOR
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TABLE_ROW_HEIGHT_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP
import com.android.tools.profilers.taskbased.common.table.leftAlignedColumnText
import com.android.tools.profilers.taskbased.common.table.rightAlignedColumnText
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
private fun ProcessListRow(selectedProcess: Common.Process,
                   onProcessSelection: (Common.Process) -> Unit,
                   process: Common.Process) {
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
          TABLE_ROW_BACKGROUND_COLOR
      )
      .padding(horizontal = TABLE_ROW_HORIZONTAL_PADDING_DP)
      .selectable(
        selected = process == selectedProcess,
        onClick = {
          val newSelectedDeviceProcess = if (process != selectedProcess) process else Common.Process.getDefaultInstance()
          onProcessSelection(newSelectedDeviceProcess)
        })
      .testTag("ProcessListRow")
  ) {
    leftAlignedColumnText(processName, rowScope = this)
    rightAlignedColumnText(text = pid.toString(), colWidth = TaskBasedUxDimensions.PID_COL_WIDTH_DP)
    rightAlignedColumnText(text = if (process.isProfileable()) "Profileable" else "Debuggable",
                           colWidth = TaskBasedUxDimensions.MANIFEST_CONFIG_COL_WIDTH_DP)
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
    leftAlignedColumnText(text = "Process name", rowScope = this)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    rightAlignedColumnText(text = "PID", colWidth = TaskBasedUxDimensions.PID_COL_WIDTH_DP)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    rightAlignedColumnText(text = "Manifest Configuration", colWidth = TaskBasedUxDimensions.MANIFEST_CONFIG_COL_WIDTH_DP)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProcessTable(processList: List<Common.Process>,
                 selectedProcess: Common.Process,
                 onProcessSelection: (Common.Process) -> Unit) {
  val listState = rememberLazyListState()

  Box(modifier = Modifier.fillMaxSize().background(TABLE_ROW_BACKGROUND_COLOR)) {
    LazyColumn(
      state = listState
    ) {
      stickyHeader {
        ProcessListHeader()
        Divider(color = TABLE_SEPARATOR_COLOR, modifier = Modifier.fillMaxWidth(), thickness = 1.dp, orientation = Orientation.Horizontal)
      }
      items(items = processList) { process ->
        ProcessListRow(selectedProcess = selectedProcess, onProcessSelection = onProcessSelection, process = process)
      }
    }

    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(listState),
      modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
    )
  }
}
