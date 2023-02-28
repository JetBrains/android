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

import com.android.tools.adtui.compose.ComposeStatus
import com.android.tools.idea.editors.liveedit.ui.MANUAL_LIVE_EDIT_ACTION_ID
import com.android.tools.idea.editors.liveedit.ui.SHOW_LOGCAT_ACTION_ID
import com.android.tools.idea.run.deployment.liveedit.LiveEditBundle.message
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.DEFAULT
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.LOWEST
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.RECOVERABLE_ERROR
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESHING
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESH_NEEDED
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.UNRECOVERABLE_ERROR
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import javax.swing.Icon

open class LiveEditStatus(
  override val icon: Icon?,
  override val title: String,
  override val description: String,
  private val mergePriority: Priority,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  override val presentation: ComposeStatus.Presentation? = null,
  val descriptionManualMode: String? = null,
  val redeployMode: RedeployMode = RedeployMode.NONE,
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

    enum class RedeployMode {
      NONE,
      REFRESH,
      RERUN,
    }

    val GradleSync = createErrorStatus(message("le.status.error.gradle_sync.description"))

    // A LiveEdit error that is not recoverable.
    @JvmStatic
    fun createErrorStatus(message: String): LiveEditStatus {
      return LiveEditStatus(
        AllIcons.General.Error,
        "Error",
        message,
        UNRECOVERABLE_ERROR
      )
    }

    // A LiveEdit error that can be resolved by rerunning.
    @JvmStatic
    fun createRerunnableErrorStatus(message: String): LiveEditStatus {
      return LiveEditStatus(
        null,
        message("le.status.out_of_date.title"),
        message,
        UNRECOVERABLE_ERROR,
        redeployMode = RedeployMode.RERUN,
        actionId = "Run"
      )
    }

    // A composition error that is not recoverable.
    @JvmStatic
    fun createRecomposeErrorStatus(message: String): LiveEditStatus {
      return LiveEditStatus(
        null,
        message("le.status.error.recompose.title"),
        String.format(
          "Application encountered unexpected exception during recomposition and is reverted to last successful composition state:<br>%s",
          if (message.length > 120) message.substring(0, 120) + "..." else message
        ),
        UNRECOVERABLE_ERROR,
        redeployMode = RedeployMode.RERUN,
        actionId = SHOW_LOGCAT_ACTION_ID
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
      null,
      "Error",
      "Live Edit encountered an unrecoverable error.",
      UNRECOVERABLE_ERROR,
      redeployMode = RedeployMode.RERUN
    )

  object DebuggerAttached :
    LiveEditStatus(
      null,
      message("le.status.error.debugger_attached.title"),
      message("le.status.error.debugger_attached.description"),
      UNRECOVERABLE_ERROR,
      redeployMode = RedeployMode.RERUN
    )

  object OutOfDate :
    LiveEditStatus(
      null,
      message("le.status.out_of_date.title"),
      message("le.status.out_of_date.description"),
      REFRESH_NEEDED,
      redeployMode = RedeployMode.REFRESH,
      actionId = MANUAL_LIVE_EDIT_ACTION_ID
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
      DEFAULT,
      descriptionManualMode = "App is up to date. Code changes will be applied to the running app on Refresh.",
    )

  object SyncNeeded :
    LiveEditStatus(
      null,
      message("le.status.error.gradle_sync.title"),
      message("le.status.error.gradle_sync.description"),
      UNRECOVERABLE_ERROR,
      redeployMode = RedeployMode.RERUN,
      actionId = "Android.SyncProject"
    )

  object UnsupportedVersion :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.error.unsupported_version.title"),
      message("le.status.error.unsupported_version.description"),
      UNRECOVERABLE_ERROR
    )

  fun unrecoverable(): Boolean {
    return mergePriority == UNRECOVERABLE_ERROR
  }

  fun merge(other: LiveEditStatus) =
    if (other.mergePriority.value > mergePriority.value) other else this
}
