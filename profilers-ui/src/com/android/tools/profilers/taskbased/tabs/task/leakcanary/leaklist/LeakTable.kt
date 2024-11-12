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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leaklist

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_OCCURRENCE_COL_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.table.LeftAlignedColumnText
import com.android.tools.profilers.taskbased.common.table.RightAlignedColumnText
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSelectableLazyListState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable
private fun LeakListRow(leak: Leak, isSelected: Boolean) {
  val name = LeakCanaryModel.getLeakClassName(leak)
  val totalLeakedKb = "${leak.retainedByteSize / 1024} kb"
  val occurrences = leak.leakTraceCount.toString()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TaskBasedUxDimensions.TABLE_ROW_HEIGHT_DP)
      .background(
        if (isSelected)
          TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR
        else
          Color.Transparent
      )
      .padding(horizontal = TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP)
      .testTag("leakListRow")
  ) {
    LeftAlignedColumnText(name, rowScope = this)
    RightAlignedColumnText(text = occurrences, colWidth = LEAKCANARY_OCCURRENCE_COL_WIDTH_DP)
    RightAlignedColumnText(text = totalLeakedKb, colWidth = LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP)
  }
}

@Composable
private fun LeakListHeader() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(TaskBasedUxDimensions.TABLE_ROW_HEIGHT_DP)
      .background(TaskBasedUxColors.TABLE_HEADER_BACKGROUND_COLOR)
      .padding(horizontal = TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP)
  ) {
    LeftAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT, rowScope = this)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT,
                           colWidth = LEAKCANARY_OCCURRENCE_COL_WIDTH_DP)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT,
                           colWidth = LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP)
  }
}

@Composable
fun LeakListContent(leaks: List<Leak>, selectedLeak: Leak?,
                    isRecording: Boolean,
                    onLeakSelection: (Leak) -> Unit) {
  Column {
      LeakListHeader()
      Divider(color = TaskBasedUxColors.TABLE_SEPARATOR_COLOR, modifier = Modifier.fillMaxWidth(), thickness = 1.dp,
              orientation = Orientation.Horizontal)
    if (leaks.isEmpty()) {
      NoLeaksMessageText(isRecording)
    } else {
      LeakTable(leaks, selectedLeak, onLeakSelection)
    }
  }
}

@Composable
fun LeakTable(leaks: List<Leak>, selectedLeak: Leak?, onLeakSelection: (Leak) -> Unit) {
  val listState = rememberSelectableLazyListState()

  Box(modifier = Modifier.fillMaxSize()) {
    SelectableLazyColumn (
      state = listState,
      selectionMode = SelectionMode.Single,
      onSelectedIndexesChanged = {
        if (it.isNotEmpty() && leaks[it.first()] != selectedLeak) {
          val newSelectedLeak = leaks[it.first()]
          onLeakSelection(newSelectedLeak)
        }
      }
    ) {
      items(items = leaks, key = { it }) {
        LeakListRow(leak = it, isSelected = (it == selectedLeak))
      }
    }
    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(listState.lazyListState),
      modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
    )
  }
}

@Composable
fun NoLeaksMessageText(isRecording: Boolean) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center) {
      if (isRecording) {
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE, maxLines = 3, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(10.dp))
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_INSTALLATION_REQUIRED_MESSAGE, maxLines = 3,
                     fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
      } else {
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_NO_LEAK_FOUND_MESSAGE, fontStyle = FontStyle.Italic, maxLines = 3,
                     textAlign = TextAlign.Center)
      }
    }
  }
}