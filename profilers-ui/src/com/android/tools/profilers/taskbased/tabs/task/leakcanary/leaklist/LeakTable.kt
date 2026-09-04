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

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakType
import com.android.tools.profilers.leakcanary.LeakCanaryModel
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_LEAK_TYPE_COL_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_OCCURRENCE_COL_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.table.LeftAlignedColumnText
import com.android.tools.profilers.taskbased.common.table.RightAlignedColumnText
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyColumn
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.lazy.rememberSingleSelectionLazyListState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/**
 * Renders a single row in the LeakCanary leak list table. Displays the leak name, type ([LeakType.APPLICATION_LEAKS] or
 * [LeakType.LIBRARY_LEAKS]), occurrence count, and total leaked memory.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeakListRow(leak: Leak, isSelected: Boolean) {
  val name = LeakCanaryModel.getLeakClassName(leak)
  val leakTypeStr =
    when (leak.type) {
      LeakType.APPLICATION_LEAKS -> TaskBasedUxStrings.LEAKCANARY_LEAK_TYPE_APP
      LeakType.LIBRARY_LEAKS -> TaskBasedUxStrings.LEAKCANARY_LEAK_TYPE_LIBRARY
    }
  val totalLeakedKb = "${leak.retainedByteSize / 1024} KB"
  val occurrences = leak.leakTraceCount.toString()

  Tooltip(tooltip = { Text(name) }) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(TaskBasedUxDimensions.TABLE_ROW_HEIGHT_DP)
          .background(if (isSelected) TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR else Color.Transparent)
          .padding(horizontal = TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP)
          .testTag("leakListRow")
    ) {
      LeftAlignedColumnText(name, rowScope = this)
      Spacer(modifier = Modifier.width(1.dp))
      RightAlignedColumnText(text = leakTypeStr, colWidth = LEAKCANARY_LEAK_TYPE_COL_WIDTH_DP)
      Spacer(modifier = Modifier.width(1.dp))
      RightAlignedColumnText(text = occurrences, colWidth = LEAKCANARY_OCCURRENCE_COL_WIDTH_DP)
      Spacer(modifier = Modifier.width(1.dp))
      RightAlignedColumnText(text = totalLeakedKb, colWidth = LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP)
    }
  }
}

/** Renders the header row for the LeakCanary leak list table with 4 columns: Leak, Leak type, Occurrences, and Total leaked. */
@Composable
private fun LeakListHeader() {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(TaskBasedUxDimensions.TABLE_HEADER_ROW_HEIGHT_DP)
        .background(TaskBasedUxColors.TABLE_HEADER_BACKGROUND_COLOR)
        .padding(horizontal = TaskBasedUxDimensions.TABLE_ROW_HORIZONTAL_PADDING_DP),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LeftAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_LEAK_HEADER_TEXT, rowScope = this)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_LEAK_TYPE_HEADER_TEXT, colWidth = LEAKCANARY_LEAK_TYPE_COL_WIDTH_DP)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_OCCURRENCES_HEADER_TEXT, colWidth = LEAKCANARY_OCCURRENCE_COL_WIDTH_DP)
    Divider(thickness = 1.dp, modifier = Modifier.fillMaxHeight(), orientation = Orientation.Vertical)
    RightAlignedColumnText(text = TaskBasedUxStrings.LEAKCANARY_TOTAL_LEAKED_HEADER_TEXT, colWidth = LEAKCANARY_TOTAL_LEAKED_COL_WIDTH_DP)
  }
}

/** Renders the content of the LeakCanary leak list, including the table header and either an empty message or the selectable leak table. */
@Composable
fun LeakListContent(
  leaks: List<Leak>,
  selectedLeak: Leak?,
  isRecording: Boolean,
  hasActiveFilter: Boolean = false,
  onLeakSelection: (Leak) -> Unit,
) {
  Column {
    LeakListHeader()
    Divider(
      color = TaskBasedUxColors.TABLE_SEPARATOR_COLOR,
      modifier = Modifier.fillMaxWidth(),
      thickness = 1.dp,
      orientation = Orientation.Horizontal,
    )
    if (leaks.isEmpty()) {
      NoLeaksMessageText(isRecording, hasActiveFilter)
    } else {
      LeakTable(leaks, selectedLeak, onLeakSelection)
    }
  }
}

@Composable
fun LeakTable(leaks: List<Leak>, selectedLeak: Leak?, onLeakSelection: (Leak) -> Unit) {
  val listState = rememberSingleSelectionLazyListState()

  Box(modifier = Modifier.fillMaxSize()) {
    SingleSelectionLazyColumn(
      state = listState,
      onSelectedIndexesChange = {
        if (it.isNotEmpty()) {
          val newSelectedLeak = leaks[it.first()]
          onLeakSelection(newSelectedLeak)
        }
      },
    ) {
      items(items = leaks, key = { it }) { LeakListRow(leak = it, isSelected = (it == selectedLeak)) }
    }
    VerticalScrollbar(scrollState = listState.lazyListState, modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
  }
}

/**
 * Displays an informational message when the leak table is empty, distinguishing between an active recording with no leaks, a finished
 * recording with no leaks, and an active filter with no matching leaks.
 */
@Composable
fun NoLeaksMessageText(isRecording: Boolean, hasActiveFilter: Boolean = false) {
  Box(modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp).padding(bottom = 28.dp), contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      if (hasActiveFilter) {
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_NO_LEAK_MATCHING_FILTER_MESSAGE, maxLines = 3, textAlign = TextAlign.Center)
      } else if (isRecording) {
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_LEAK_LIST_EMPTY_INITIAL_MESSAGE, maxLines = 3, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(10.dp))
      } else {
        EllipsisText(text = TaskBasedUxStrings.LEAKCANARY_NO_LEAK_FOUND_MESSAGE, maxLines = 3, textAlign = TextAlign.Center)
      }
    }
  }
}
