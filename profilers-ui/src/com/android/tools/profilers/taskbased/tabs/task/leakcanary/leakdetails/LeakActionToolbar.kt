/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import icons.StudioIconsCompose
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * A composable for the content of the leak action toolbar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeakActionToolbar(
  selectedLeak: Leak,
  onExpandAll: () -> Unit,
  onCollapseAll: () -> Unit
) {
  Row(
    modifier = Modifier
      .padding(horizontal = TaskBasedUxDimensions.TASK_ACTION_BAR_ACTION_HORIZONTAL_SPACE_DP)
      .fillMaxWidth()
      .height(TaskBasedUxDimensions.TABLE_HEADER_ROW_HEIGHT_DP),
    horizontalArrangement = Arrangement.End
  ) {
    Tooltip(
      tooltip = {
        Column(horizontalAlignment = Alignment.Start) {
          Text(TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL)
          Text(TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL_SHORTCUT, color = JewelTheme.globalColors.text.info)
        }
      }
    ) {
      IconButton(onClick = onExpandAll) {
        Icon(
          key = StudioIconsCompose.Profiler.Toolbar.ExpandSession,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_EXPAND_ALL,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
    Tooltip(
      tooltip = {
        Column(horizontalAlignment = Alignment.Start) {
          Text(TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL)
          Text(TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL_SHORTCUT, color = JewelTheme.globalColors.text.info)
        }
      }
    ) {
      IconButton(onClick = onCollapseAll) {
        Icon(
          key = StudioIconsCompose.Profiler.Toolbar.CollapseSession,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_COLLAPSE_ALL,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
    Tooltip(
      tooltip = { Text(TaskBasedUxStrings.LEAKCANARY_COPY_TO_CLIPBOARD) }
    ) {
      IconButton(onClick = { copyLeakToClipboard(selectedLeak.toString()) }) {
        Icon(
          key = AllIconsKeys.Actions.Copy,
          contentDescription = TaskBasedUxStrings.LEAKCANARY_COPY_TO_CLIPBOARD,
          modifier = Modifier.padding(TaskBasedUxDimensions.TASK_ACTION_BAR_CONTENT_PADDING_DP),
        )
      }
    }
  }
}

/**
 * Copies a simplified text representation of the selected leak to the system clipboard.
 */
internal var copyLeakToClipboard: (String) -> Unit = { content ->
  val clipboard = Toolkit.getDefaultToolkit().systemClipboard
  clipboard.setContents(StringSelection(content), null)
}