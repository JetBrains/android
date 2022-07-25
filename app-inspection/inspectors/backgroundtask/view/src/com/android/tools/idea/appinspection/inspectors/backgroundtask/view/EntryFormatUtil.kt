/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import icons.StudioIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.Icon

fun Long.toFormattedTimeString(): String {
  val formatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
  return if (this == -1L) "-" else formatter.format(Date(this))
}

fun String.capitalizedName() = lowercase(Locale.getDefault()).capitalize(Locale.getDefault())

fun WorkInfo.State.isFinished() = this == WorkInfo.State.SUCCEEDED || this == WorkInfo.State.FAILED || this == WorkInfo.State.CANCELLED

fun WorkInfo.State.icon() = when (this) {
  WorkInfo.State.ENQUEUED -> StudioIcons.LayoutEditor.Palette.CHRONOMETER
  WorkInfo.State.RUNNING -> AnimatedIcon.Default()
  WorkInfo.State.BLOCKED -> AllIcons.RunConfigurations.TestPaused
  WorkInfo.State.CANCELLED -> AllIcons.RunConfigurations.TestIgnored
  WorkInfo.State.FAILED -> AllIcons.RunConfigurations.ToolbarError
  WorkInfo.State.SUCCEEDED -> AllIcons.RunConfigurations.TestPassed
  else -> null
}

fun BackgroundTaskEntry.icon(): Icon? {
  return when (this) {
    is AlarmEntry -> {
      when (status) {
        AlarmEntry.State.SET.name -> StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK
        AlarmEntry.State.FIRED.name -> AllIcons.RunConfigurations.TestPassed
        AlarmEntry.State.CANCELLED.name -> StudioIcons.Common.CLOSE
        else -> null
      }
    }
    is JobEntry -> {
      when (status) {
        JobEntry.State.SCHEDULED.name -> StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK
        JobEntry.State.STARTED.name -> AnimatedIcon.Default()
        JobEntry.State.STOPPED.name -> AllIcons.RunConfigurations.TestIgnored
        JobEntry.State.FINISHED.name -> AllIcons.RunConfigurations.TestPassed
        else -> null
      }
    }
    is WakeLockEntry -> {
      when (status) {
        WakeLockEntry.State.ACQUIRED.name -> StudioIcons.LayoutEditor.Toolbar.LOCK
        WakeLockEntry.State.RELEASED.name -> StudioIcons.LayoutEditor.Toolbar.UNLOCK
        else -> null
      }
    }
    is WorkEntry -> {
      getWorkInfo().state.icon()
    }
    else -> null
  }
}
