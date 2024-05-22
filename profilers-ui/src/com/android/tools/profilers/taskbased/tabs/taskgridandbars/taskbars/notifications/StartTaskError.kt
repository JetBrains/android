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
import com.android.tools.profilers.taskbased.common.constants.strings.TaskBasedUxStrings.START_TASK_SELECTION_ERROR_ICON_DESC
import icons.StudioIconsCompose

@Composable
fun StartTaskError(errorMessage: String, isCollapsed: Boolean) {
  CollapsibleNotification(mainText = errorMessage, tooltip = null,
                          iconPainter = StudioIconsCompose.Common.Error().getPainter().value,
                          iconDescription = START_TASK_SELECTION_ERROR_ICON_DESC, isCollapsed = isCollapsed)
}