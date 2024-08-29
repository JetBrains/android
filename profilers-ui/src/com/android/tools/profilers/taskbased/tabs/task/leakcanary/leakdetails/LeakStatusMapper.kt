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
package com.android.tools.profilers.taskbased.tabs.task.leakcanary.leakdetails

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.tools.leakcanarylib.data.LeakingStatus
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

fun getLeakStatusIcon(leakingStatus: LeakingStatus): IntelliJIconKey {
  return when (leakingStatus) {
    LeakingStatus.YES -> StudioIconsCompose.Profiler.Memoryleakstask.LeakYes
    LeakingStatus.UNKNOWN -> StudioIconsCompose.Profiler.Memoryleakstask.LeakMaybe
    LeakingStatus.NO -> StudioIconsCompose.Profiler.Memoryleakstask.LeakNo
  }
}

fun getLeakStatusColor(leakingStatus: LeakingStatus): Color {
  return when(leakingStatus) {
    LeakingStatus.YES -> Color.Red
    LeakingStatus.UNKNOWN -> Color.Yellow
    LeakingStatus.NO -> Color.LightGray
  }
}

@Composable
fun LeakIcon(status: LeakingStatus) {
  return Icon(
    getLeakStatusIcon(status),
    contentDescription = status.name,
    iconClass = StudioIconsCompose::class.java
  )
}

@Composable
fun VerticalLeakStatusLine(status: LeakingStatus) {
  Divider(orientation = Orientation.Vertical,
          modifier = Modifier.fillMaxHeight().heightIn(min = 10.dp).testTag("verticalLeakLine"),
          color = getLeakStatusColor(status)
  )
}

