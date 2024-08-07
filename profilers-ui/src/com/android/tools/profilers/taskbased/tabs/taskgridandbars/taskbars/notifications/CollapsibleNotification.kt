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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskbars.notifications

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TASK_NOTIFICATION_ICON_TEXT_HORIZONTAL_SPACE_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TOOLTIP_MAX_WIDTH_DP
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions.TOOLTIP_VERTICAL_SPACING_DP
import main.utils.tooltips.TooltipStyleFactory.createTooltipStyle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import kotlin.time.Duration

/**
 * The following component controls the UI of a notification in two states, determined by the "isCollapsible" boolean:
 *
 * Non-collapsed:
 *    UI -> [ICON] [MAIN TEXT]
 *    where [ICON] [MAIN TEXT] has an optional tooltip for more details.
 * Collapsed:
 *    UI -> [ICON]
 *    where the icon that has a tooltip that combines the main text and the optional supplied tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsibleNotification(mainText: String,
                            tooltip: (@Composable () -> Unit)? = null,
                            iconKey: IntelliJIconKey,
                            iconDescription: String,
                            isCollapsed: Boolean) {
  if (isCollapsed || tooltip != null) {
    Tooltip(
      tooltip = {
        Column(modifier = Modifier.widthIn(max = TOOLTIP_MAX_WIDTH_DP)) {
          if (isCollapsed) {
            Text(mainText, fontWeight = if (tooltip != null) FontWeight.SemiBold else null)
          }
          if (isCollapsed && tooltip != null) {
            Spacer(modifier = Modifier.height(TOOLTIP_VERTICAL_SPACING_DP))
          }
          tooltip?.let { it() }
        }
      },
      content = { NotificationIconAndText(mainText, iconKey, iconDescription, isCollapsed) },
      style = createTooltipStyle(Duration.ZERO)
    )
  }
  else {
    NotificationIconAndText(mainText, iconKey, iconDescription, isCollapsed)
  }
}

@Composable
private fun NotificationIconAndText(mainText: String, iconKey: IntelliJIconKey, iconDescription: String, isCollapsed: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      key = iconKey,
      contentDescription = iconDescription,
    )
    if (!isCollapsed) {
      Spacer(modifier = Modifier.width(TASK_NOTIFICATION_ICON_TEXT_HORIZONTAL_SPACE_DP))
      Text(mainText)
    }
  }
}