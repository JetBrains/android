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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.TOOLTIP_VERTICAL_SPACING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.INFO_ICON_DESC
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.PROFILEABLE_PREFERRED_REBUILD_INSTRUCTION_TOOLTIP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxStrings.PROFILEABLE_PREFERRED_WARNING_TOOLTIP
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ProfileablePreferredWarning(isPreferredProcessSelected: Boolean, isCollapsed: Boolean) {
  val tooltip: @Composable () -> Unit = {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(PROFILEABLE_PREFERRED_WARNING_TOOLTIP)
      if (isPreferredProcessSelected) {
        Spacer(modifier = Modifier.height(TOOLTIP_VERTICAL_SPACING_DP))
        Text(PROFILEABLE_PREFERRED_REBUILD_INSTRUCTION_TOOLTIP)
      }
    }
  }

  CollapsibleNotification(mainText = PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT, tooltip = tooltip,
                          iconPainter = StudioIconsCompose.Common.WarningInline().getPainter().value,
                          iconDescription = INFO_ICON_DESC, isCollapsed = isCollapsed)
}