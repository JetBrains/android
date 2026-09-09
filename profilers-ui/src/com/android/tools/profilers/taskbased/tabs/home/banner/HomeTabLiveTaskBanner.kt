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
package com.android.tools.profilers.taskbased.tabs.home.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link

/**
 * Banner displayed at the top of the Profiler Home tab when a live profiler task is running in an editor tab and is currently not focused.
 *
 * @param taskName the user-facing name of the active profiler task (e.g. "Live Telemetry")
 * @param onReturnToTaskClick callback invoked when the user clicks the "Return to task ↗" link
 */
@Composable
fun HomeTabLiveTaskBanner(taskName: String, onReturnToTaskClick: () -> Unit) {
  Column {
    HomeTabLiveTaskBannerBorderLine()
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR)
          .padding(TaskBasedUxDimensions.RECORDING_BANNER_PADDING_DP),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(key = StudioIconsCompose.Common.Info, contentDescription = TaskBasedUxStrings.INFO_ICON_DESC)
      Spacer(modifier = Modifier.width(8.dp))
      EllipsisText(text = TaskBasedUxStrings.LIVE_TASK_RUNNING_IN_EDITOR_MESSAGE.format(taskName))
      Spacer(modifier = Modifier.weight(1f))
      Link(TaskBasedUxStrings.RETURN_TO_TASK_LINK_TEXT, onClick = onReturnToTaskClick, overflow = TextOverflow.Ellipsis)
    }
    HomeTabLiveTaskBannerBorderLine()
  }
}

/** Horizontal divider border line used above and below the banner. */
@Composable
private fun HomeTabLiveTaskBannerBorderLine() {
  Divider(
    orientation = Orientation.Horizontal,
    color = TaskBasedUxColors.BANNER_BORDER_COLOR,
    modifier = Modifier.fillMaxWidth(),
  )
}
