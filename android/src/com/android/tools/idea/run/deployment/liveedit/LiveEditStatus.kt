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

import com.android.tools.adtui.status.IdeStatus
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.editors.liveedit.ui.REFRESH_ACTION_ID
import com.android.tools.idea.editors.liveedit.ui.SHOW_LOGCAT_ACTION_ID
import com.android.tools.idea.run.deployment.liveedit.LiveEditBundle.message
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.DEFAULT
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.DISABLED
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.RECOVERABLE_ERROR
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESHING
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.REFRESH_NEEDED
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.DISABLED_WITH_MESSAGE
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.Priority.UNRECOVERABLE_ERROR
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import java.lang.Exception
import javax.swing.Icon

open class LiveEditStatus(
  override val icon: Icon?,
  override val title: String,
  override val description: String,
  private val mergePriority: Priority,
  /** When true, the refresh icon will be displayed next to the notification chip. */
  override val presentation: IdeStatus.Presentation? = null,
  val descriptionManualMode: String? = null,
  val redeployMode: RedeployMode = RedeployMode.NONE,
  val actionId: String? = null,
  override val shouldSimplify: Boolean = false,
  ) : IdeStatus {
  companion object {
    // A simple priority system that is used when multiple LiveEditStatus need to be merged.
    // The high the value is, the more important the status is, and thus takes precedence.
    enum class Priority(val value: Int) {
      DISABLED(0), // This should remain as the first item in the enum.
      DISABLED_WITH_MESSAGE(1),
      DEFAULT(2),
      REFRESHING(3),
      REFRESH_NEEDED(4),
      RECOVERABLE_ERROR(5),
      UNRECOVERABLE_ERROR(6),
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
    fun createRecomposeErrorStatus(name: String, message: String, recoverable: Boolean): LiveEditStatus {
      return LiveEditStatus(
        null,
        message("le.status.error.recompose.title"),
        String.format(
          "%s during recomposition and is reverted to last successful composition state:<br>%s",
          name, if (message.length > 120) message.substring(0, 120) + "..." else message
        ),
        if (recoverable) RECOVERABLE_ERROR else UNRECOVERABLE_ERROR,
        redeployMode = RedeployMode.RERUN,
        actionId = SHOW_LOGCAT_ACTION_ID
      )
    }

    @JvmStatic
    fun createRecomposeRetrievalErrorStatus(exception: Exception): LiveEditStatus {
      return LiveEditStatus(
        null,
        message("le.status.error.recompose.title"),
        String.format(
          "%s during recomposition status retrieval.", exception.javaClass.name
        ),
        RECOVERABLE_ERROR,
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

  object Disabled : LiveEditStatus(AllIcons.General.Warning, "Live Edit disabled", "Live Edit is disabled.", DISABLED)

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
      AllIcons.Toolwindows.ToolWindowDebugger,
      message("le.status.error.debugger_attached.title"),
      message("le.status.error.debugger_attached.description"),
      UNRECOVERABLE_ERROR,
      shouldSimplify = true ,
      actionId = REFRESH_ACTION_ID,
      )

  object OutOfDate :
    LiveEditStatus(
      null,
      message("le.status.out_of_date.title"),
      message("le.status.out_of_date.description"),
      REFRESH_NEEDED,
      redeployMode = RedeployMode.REFRESH,
      actionId = REFRESH_ACTION_ID
    )

  object NoMultiDeploy :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.out_of_date.title"),
      message("le.status.no_multi_deploy.description"),
      DISABLED_WITH_MESSAGE
    )

  object Loading :
    LiveEditStatus(
      AnimatedIcon.Default.INSTANCE,
      message("le.status.loading.title"),
      message("le.status.loading.description"),
      REFRESHING,
      shouldSimplify = true
    )

  object InProgress :
    LiveEditStatus(
      AnimatedIcon.Default.INSTANCE,
      message("le.status.in_progress.title"),
      message("le.status.in_progress.description"),
      REFRESHING,
      shouldSimplify = true
    )

  object CopyingPsi :
    LiveEditStatus(
      AnimatedIcon.Default.INSTANCE,
      message("le.status.pre_compiling.title"),
      message("le.status.pre_compiling.description"),
      REFRESHING,
      shouldSimplify = true
    )

  object UpToDate :
    LiveEditStatus(
      AllIcons.General.InspectionsOK,
      message("le.status.up_to_date.title"),
      message("le.status.up_to_date.description_auto"),
      DEFAULT,
      descriptionManualMode = "App is up to date. Code changes will be applied to the running app on Refresh.",
      shouldSimplify = true
    ) {
      override val description
        get() = if (LiveEditApplicationConfiguration.getInstance().leTriggerMode ==
                    LiveEditService.Companion.LiveEditTriggerMode.AUTOMATIC) {
          message("le.status.up_to_date.description_auto")
        } else {
          message("le.status.up_to_date.description_manual")
        }
    }

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

  object UnsupportedVersionOtherDevice :
    LiveEditStatus(
      AllIcons.General.Warning,
      message("le.status.error.unsupported_version.title"),
      message("le.status.error.unsupported_version_other.description"),
      UNRECOVERABLE_ERROR
    )

  fun unrecoverable(): Boolean {
    return mergePriority == UNRECOVERABLE_ERROR
  }

  fun merge(other: LiveEditStatus) =
    if (other.mergePriority.value > mergePriority.value) other else this
}
