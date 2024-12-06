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
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.DEBUGGABLE_REBUILD_INSTRUCTION_TOOLTIP
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.START_TASK_SELECTION_ERROR_ICON_DESC
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.getStartTaskErrorMessage
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.getStartTaskErrorNotificationText
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError
import com.android.tools.profilers.taskbased.home.StartTaskSelectionError.StarTaskSelectionErrorCode
import icons.StudioIconsCompose

@Composable
fun StartTaskError(error: StartTaskSelectionError) {
  val errorCode = error.starTaskSelectionErrorCode
  var subText = error.actionableInfo
  if (errorCode == StarTaskSelectionErrorCode.TASK_REQUIRES_DEBUGGABLE_PROCESS) {
    subText = DEBUGGABLE_REBUILD_INSTRUCTION_TOOLTIP
  }

  NotificationWithTooltip(notificationText = getStartTaskErrorNotificationText(error),
                          tooltipMainText = getStartTaskErrorMessage(errorCode), tooltipSubText = subText,
                          iconKey = StudioIconsCompose.Common.Error, iconDescription = START_TASK_SELECTION_ERROR_ICON_DESC)
}