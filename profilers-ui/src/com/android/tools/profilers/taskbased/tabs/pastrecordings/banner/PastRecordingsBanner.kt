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
package com.android.tools.profilers.taskbased.tabs.pastrecordings.banner

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
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.PAST_RECORDINGS_BANNER_BORDER_COLOR
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors.TABLE_ROW_SELECTION_BACKGROUND_COLOR
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.RECORDING_BANNER_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.DONT_SHOW_AGAIN_TITLE
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.RECORDING_BANNER_MESSAGE
import com.android.tools.profilers.taskbased.common.text.EllipsisText
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PastRecordingsBanner(onBannerClose: () -> Unit, onBannerDoNotAskAgainClick: () -> Unit) {
  Column {
    PastRecordingsBannerBorderLine()
    Row(modifier = Modifier.background(TABLE_ROW_SELECTION_BACKGROUND_COLOR).padding(RECORDING_BANNER_PADDING_DP),
        verticalAlignment = Alignment.CenterVertically) {
      Icon(
        painter = StudioIconsCompose.Common.InfoInline().getPainter().value,
        contentDescription = "Info"
      )
      Spacer(modifier = Modifier.width(8.dp))
      EllipsisText(text = RECORDING_BANNER_MESSAGE)
      Spacer(modifier = Modifier.weight(1f))
      Link(DONT_SHOW_AGAIN_TITLE, onClick = { onBannerDoNotAskAgainClick() }, overflow = TextOverflow.Ellipsis)
      Spacer(modifier = Modifier.width(8.dp))
      IconButton(onClick = { onBannerClose() }) {
        Icon(painter = StudioIconsCompose.Common.Close().getPainter().value, contentDescription = "Close")
      }
    }
    PastRecordingsBannerBorderLine()
  }
}

@Composable
private fun PastRecordingsBannerBorderLine() {
  Divider(orientation = Orientation.Horizontal, color = PAST_RECORDINGS_BANNER_BORDER_COLOR, modifier = Modifier.fillMaxWidth())
}