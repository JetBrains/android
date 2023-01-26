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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.compose.ComposeStatus
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.run.deployment.liveedit.LiveEditBundle.message
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.DEFAULT
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.LOWEST
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.RECOVERABLE_ERROR
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESHING
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESH_NEEDED
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.UNRECOVERABLE_ERROR
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import javax.swing.Icon

open class LiveEditStatus(
  override val icon: Icon?,
  override val title: String,
  override val description: String,
  private val mergePriority: Priority,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  override val hasRefreshIcon: Boolean = false,
  override val presentation: ComposeStatus.Presentation? = null,
  val actionId: String? = null,
) : ComposeStatus {
  companion object {
    // A simple priority system that is used when multiple LiveEditStatus need to be merged.
    // The high the value is, the more important the status is, and thus takes precedence.
    enum class Priority(val value: Int) {
      LOWEST(0), // This should remain as the first item in the enum.
      DEFAULT(1),
      REFRESHING(2),
      REFRESH_NEEDED(3),
      RECOVERABLE_ERROR(4),
      UNRECOVERABLE_ERROR(5),
    }

    val GradleSync = createErrorStatus(message("le.status.error.gradle_sync.description"))

    // A LiveEdit error that is not recoverable.
    @JvmStatic
    fun createErrorStatus(message: String): LiveEditStatus {
      return LiveEditStatus(
        ColoredIconGenerator.generateColoredIcon(AllIcons.General.InspectionsError, JBColor.RED),
        "Error",
        message,
        UNRECOVERABLE_ERROR,
        hasRefreshIcon = true
      )
    }

    @JvmStatic
    fun createComposeVersionError(message: String): LiveEditStatus {
      return LiveEditStatus(
        AllIcons.General.Warning,
        "Compose Version Error",
        message,
        UNRECOVERABLE_ERROR
      )
    }

    @JvmStatic
    fun createPausedStatus(message: String): LiveEditStatus {
      return LiveEditStatus(
        AllIcons.General.InspectionsPause,
        "Paused",
        message,
        RECOVERABLE_ERROR
      )
    }
  }

  object Disabled : LiveEditStatus(null, "", "", LOWEST)

  object UnrecoverableError :
    LiveEditStatus(
      AllIcons.General.Error,
      "Error",
      "Live Edit encountered an unrecoverable error.",
      UNRECOVERABLE_ERROR,
      hasRefreshIcon = true
    )

  object DebuggerAttached :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.error.debugger_attached.title"),
      message("le.status.error.debugger_attached.description"),
      UNRECOVERABLE_ERROR,
      hasRefreshIcon = true
    )

  object RecomposeNeeded :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.recompose_needed.title"),
      message("le.status.recompose_needed.description"),
      UNRECOVERABLE_ERROR,
      actionId = "android.deploy.livedit.recompose",
      hasRefreshIcon = true
    )

  object RecomposeError :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.error.recompose.title"),
      message("le.status.error.recompose.description"),
      UNRECOVERABLE_ERROR,
    )

  object OutOfDate :
    LiveEditStatus(
      null,
      message("le.status.out_of_date.title"),
      message("le.status.out_of_date.description"),
      REFRESH_NEEDED,
      actionId = LiveEditService.PIGGYBACK_ACTION_ID,
      hasRefreshIcon = true
    )

  object Loading :
    LiveEditStatus(
      AnimatedIcon.Default.INSTANCE,
      message("le.status.loading.title"),
      message("le.status.loading.description"),
      REFRESHING
    )

  object InProgress :
    LiveEditStatus(
      AnimatedIcon.Default.INSTANCE,
      message("le.status.in_progress.title"),
      message("le.status.in_progress.description"),
      REFRESHING
    )

  object UpToDate :
    LiveEditStatus(
      AllIcons.General.InspectionsOK,
      message("le.status.up_to_date.title"),
      message("le.status.up_to_date.description"),
      DEFAULT
    )

  object SyncNeeded :
    LiveEditStatus(
      ColoredIconGenerator.generateColoredIcon(AllIcons.General.InspectionsError, JBColor.RED),
      message("le.status.error.gradle_sync.title"),
      message("le.status.error.gradle_sync.description"),
      UNRECOVERABLE_ERROR
    )

  fun unrecoverable(): Boolean {
    return mergePriority == UNRECOVERABLE_ERROR
  }

  fun merge(other: LiveEditStatus) =
    if (other.mergePriority.value > mergePriority.value) other else this
}
