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

import androidx.compose.runtime.Composable
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.INFO_ICON_DESC
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.PROFILEABLE_REBUILD_INSTRUCTION_TOOLTIP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.PROFILEABLE_PREFERRED_WARNING_TOOLTIP
import icons.StudioIconsCompose

@Composable
fun ProfileablePreferredWarning(isPreferredProcessSelected: Boolean) {
  var subText = PROFILEABLE_PREFERRED_WARNING_TOOLTIP
  if (isPreferredProcessSelected) {
    subText += "\n\n$PROFILEABLE_REBUILD_INSTRUCTION_TOOLTIP"
  }

  NotificationWithTooltip(notificationText = PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT,
                          tooltipMainText = PROFILEABLE_PREFERRED_WARNING_MAIN_TEXT, tooltipSubText = subText,
                          iconKey = StudioIconsCompose.Common.Warning, iconDescription = INFO_ICON_DESC)
}