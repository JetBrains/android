/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.home.processlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions.RESTART_ACTION_CONTENT_PADDING_DP
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons.DEBUGGABLE_PROFILER_ICON
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons.PROFILEABLE_PROFILER_ICON
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxIcons.RESTART_ICON
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.Text

private fun RestartActionDropdownItem(isProfileable: Boolean, buildAndLaunch: (Boolean) -> Unit, menuScope: MenuScope) {
  with(menuScope) {
    selectableItem(selected = false, onClick = { buildAndLaunch(isProfileable) }) {
      Row(modifier = Modifier.padding(RESTART_ACTION_CONTENT_PADDING_DP), verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        val actionTitle = "Restart as ${if (isProfileable) "Profileable" else "Debuggable"}"
        val (iconPath, iconClass, _) = if (isProfileable) PROFILEABLE_PROFILER_ICON else DEBUGGABLE_PROFILER_ICON
        Icon(
          resource = iconPath,
          contentDescription = actionTitle,
          iconClass = iconClass,
        )
        Text(actionTitle)
      }
    }
  }
}

/**
 * The RestartActionDropdown composable, powered by the buildAndLaunch function, enables users to rebuild and relaunch the main process.
 * The function accepts a boolean parameter to specify whether the main process should restart as profileable or debuggable.
 */
@Composable
fun RestartActionDropdown(buildAndLaunch: (Boolean) -> Unit) {
  Dropdown(modifier = Modifier.padding(RESTART_ACTION_CONTENT_PADDING_DP), enabled = true, menuContent = {
    RestartActionDropdownItem(isProfileable = false, buildAndLaunch, this)
    RestartActionDropdownItem(isProfileable = true, buildAndLaunch, this)
  }) {
    Icon(
      resource = RESTART_ICON.path,
      contentDescription = "Restart main process",
      iconClass = RESTART_ICON.iconClass,
      modifier = Modifier.padding(RESTART_ACTION_CONTENT_PADDING_DP)
    )
  }
}