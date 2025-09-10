/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.gradle.project.sync.AutoSyncSettingStore
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent.ChangeSource
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.IndicatorType
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.UserAction
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.newBuilder
import com.intellij.ide.ActivityTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsConfiguration
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import org.jetbrains.android.util.AndroidBundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.distinctBy

@VisibleForTesting
const val SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID = "Syncing project is recommended"

/**
 * This flag indicate at what date should the app-wide temporary Sync Due notification snooze expire.
 * When checking if Sync Due dialog or notification should be shown - this will be compared against the current date.
 */
const val SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE = "gradle.settings.autoSync.notification.snooze.expiration.date"

/**
 * This flag indicate when was the project specific Sync Due notification snooze set.
 * When the app-wide Sync Due setting is being changed - the date of that even is being stored, so
 * project specific snooze can be ignored.
 */
const val SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP = "gradle.settings.autoSync.notification.snooze.project.set.on.timestamp"
const val SYNC_DUE_DIALOG_SHOWN = "gradle.settings.autoSync.dialog.shown"

object SyncDueMessage {

  private val LOG = Logger.getInstance(SyncDueMessage::class.java)

  @VisibleForTesting
  var timeProvider: () -> Long = { System.currentTimeMillis() }

  /**
   * Checks if Sync Due messages are snoozed, and chooses between dialog and notification
   * depending on showing for the first time.
   */
  fun maybeShow(project: Project): Boolean {
    return when {
      isSnoozed(project) -> {
        LOG.warn("Auto Sync would now be triggered, but it is currently switched off and related notifications snoozed.")
        trackSuppressedSync(type = IndicatorType.HIDDEN_DUE_SNOOZE, action = UserAction.NONE)
        false
      }

      isShownFirstTime() -> {
        showAsDialog(project)
        true
      }

      else -> {
        showAsNotification(project)
        true
      }
    }
  }

  private class SyncDueNotification(project: Project) : Notification(
    SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID,
    AndroidBundle.message("gradle.settings.autoSync.notification.message", ApplicationNamesInfo.getInstance().fullProductName),
    NotificationType.WARNING,
  ) {
    init {
      NotificationsConfiguration.getNotificationsConfiguration().setDisplayType(SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID,
                                                                                NotificationDisplayType.STICKY_BALLOON)
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.notification.action.sync")) {
        override fun actionPerformed(e: AnActionEvent) {
          expire()
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.SINGLE_SYNC)
          requestProjectSync(project)
          ActivityTracker.getInstance().inc()
          hideBalloon()
        }
      })
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.notification.action.enableAutoSync")) {
        override fun actionPerformed(e: AnActionEvent) {
          expire()
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.ENABLE_AUTO_SYNC)
          trackAutoSyncEnabled(changeSource = ChangeSource.NOTIFICATION)
          AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Default
          requestProjectSync(project)
          ActivityTracker.getInstance().inc()
          hideBalloon()
        }
      })
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.notification.action.snooze")) {
        override fun actionPerformed(e: AnActionEvent) {
          expire()
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.SNOOZE)
          snoozeTemporarilyForAllProjects()
          hideBalloon()
        }
      })
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.notification.action.snooze.long")) {
        override fun actionPerformed(e: AnActionEvent) {
          expire()
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.SNOOZE)
          snoozeIndefinitelyForProject(project)
          hideBalloon()
        }
      })
    }

    override fun setBalloon(balloon: Balloon) {
      super.setBalloon(balloon)
      balloon.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          super.onClosed(event)
          hideBalloon()
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.CLOSED)
        }
      })
    }
  }

  private fun requestProjectSync(project: Project) {
    GradleSyncInvoker.getInstance()
      .requestProjectSync(project, GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST), null);
  }

  private fun showAsNotification(project: Project) {
    ActivityTracker.getInstance().inc()
    dismissNotificationIfPresent(project)
    Notifications.Bus.notify(SyncDueNotification(project), project)
  }

  private fun showAsDialog(project: Project) {
    ApplicationManager.getApplication().invokeAndWait(
      {
        val dialog = SyncDueDialog()
        dialog.show()
        when (SyncDueDialogSelection.of(dialog)) {
          SyncDueDialogSelection.SyncOnce -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.SINGLE_SYNC)
            requestProjectSync(project)
          }
          SyncDueDialogSelection.SyncAlways -> {
            trackAutoSyncEnabled(changeSource = ChangeSource.DIALOG)
            AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Default
            requestProjectSync(project)
          }
          SyncDueDialogSelection.Close -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.CLOSED)
          }
          SyncDueDialogSelection.CloseAndSnoozeTodayForAllProjects -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.SNOOZE)
            snoozeTemporarilyForAllProjects()
          }
          SyncDueDialogSelection.CloseAndSnoozeIndefinitelyForProject -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.SNOOZE)
            snoozeIndefinitelyForProject(project)
          }
        }

        markAsShownDialog()
      }, ModalityState.nonModal()
    )
  }

  fun dismissNotificationIfPresent(project: Project) {
    NotificationsManager.getNotificationsManager()
      .getNotificationsOfType<SyncDueNotification>(SyncDueNotification::class.java, project)
      .onEach {
        it.expire()
        it.hideBalloon()
      }
  }

  fun getProjectsWhereSyncIsDue(): List<Project> = ProjectManager.getInstance().openProjects.filter {
    it.hasSyncDueNotificationShown() || it.hasNoInitialSyncPerformed()
  }

  private fun Project.hasSyncDueNotificationShown(): Boolean = NotificationsManager.getNotificationsManager().getNotificationsOfType(
    SyncDueNotification::class.java, this).isNotEmpty()

  private fun Project.hasNoInitialSyncPerformed(): Boolean = ProjectStructure.getInstance(this).androidPluginVersions.allVersions.isEmpty()

  private fun isShownFirstTime(): Boolean {
    return PropertiesComponent.getInstance().getValue(SYNC_DUE_DIALOG_SHOWN).isNullOrBlank()
  }

  private fun markAsShownDialog() {
    PropertiesComponent.getInstance().setValue(SYNC_DUE_DIALOG_SHOWN, true)
  }

  /**
   * Checks if a snooze is still active for current project.
   * @return `true` if notifications are not meant to be delivered (either indefinitely for current
   *  project, or temporarily for all projects); `false` otherwise
   */
  private fun isSnoozed(project: Project): Boolean {
    if (isProjectSpecificSnoozeActive(project)) {
      return true
    }
    return isTemporarySnoozeActive()
  }

  fun isTemporarySnoozeActive(): Boolean {
    val snoozeExpirationDate = PropertiesComponent.getInstance().getValue(SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE) ?: return false
    val today = timeProvider.invoke().formatDateAtDefaultTimezone()
    return snoozeExpirationDate > today
  }

  fun isProjectSpecificSnoozeActive(project: Project): Boolean {
    if (!PropertiesComponent.getInstance(project).isValueSet(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP)) {
      // snooze not set
      return false
    }
    val lastSettingChangeTimestamp = AutoSyncSettingStore.lastAutoSyncBehaviorChangeTimestamp()
    if (lastSettingChangeTimestamp == null) {
      // snooze set & no setting change since the snooze - still valid
      return true
    } else {
      val snoozeSetAtTimestamp = PropertiesComponent.getInstance(project).getValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP)?.toLong() ?: return false
      // snooze set but settings changed after - no longer valid
      return lastSettingChangeTimestamp < snoozeSetAtTimestamp
    }
  }

  /**
   * Disable Sync Due notifications until the next day. If it's 11pm, that means - for another hour.
   */
  @VisibleForTesting
  fun snoozeTemporarilyForAllProjects() {
    // Storing DD-MM-YYYY allows to not couple snooze setting to timezone.
    val snoozeExpirationDate = (timeProvider.invoke() + 24 * 3600 * 1000).formatDateAtDefaultTimezone()
    PropertiesComponent.getInstance().setValue(SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE, snoozeExpirationDate)
  }

  /**
   * Disable Sync Due notifications for this project. This will only expire when Auto Sync settings change.
   */
  @VisibleForTesting
  fun snoozeIndefinitelyForProject(project: Project) {
    PropertiesComponent.getInstance(project).setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, timeProvider.invoke().toString())
  }


  private fun trackAutoSyncEnabled(changeSource: ChangeSource) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.AUTO_SYNC_SETTING_CHANGE)
        .setAutoSyncSettingChangeEvent(
          AutoSyncSettingChangeEvent.newBuilder()
            .setState(true)
            .setChangeSource(changeSource)
            .build()
        )
    )
  }

  private fun trackSuppressedSync(type: IndicatorType, action: UserAction) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.SUPPRESSED_SYNC)
        .setSuppressedSyncEvent(
          newBuilder()
            .setAction(action)
            .setType(type)
            .build()
        )
    )
  }

  /**
   * Converts time (millis) into DD-MM-YYYY format.
   */
  private fun Long.formatDateAtDefaultTimezone(): String {
    val dateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ISO_LOCAL_DATE.format(dateTime)
  }

  /**
   * Provides a description of current Sync reminders snooze status.
   * @return
   *  - "Reminders snoozed until tomorrow." if app wide snooze is on.
   *  - "Reminders snoozed for this project"
   *  - "Reminders snoozed for: "projectA", "projectB".
   *  - null if no currently open projects are snoozed and there is no app-wide snooze
   */
  fun getSnoozedProjectsSummaryNote(): String? {
    if (isTemporarySnoozeActive()) {
      return AndroidBundle.message("gradle.settings.autoSync.note")
    }
    val snoozedProjects = getSnoozedProjects()
    if (snoozedProjects.isEmpty()) {
      return null
    }
    return AndroidBundle.message("gradle.settings.autoSync.note.specific", if (ProjectManager.getInstance().openProjects.size == 1) {
      "this project."
    }
    else {
      // If project names aren't unique - show paths for clarity.
      val showProjectPaths = snoozedProjects.distinctBy { it.name }.count() != snoozedProjects.size
      snoozedProjects.joinToString(
          prefix = if (snoozedProjects.size == 1) "project " else "projects: ",
          postfix = if (!showProjectPaths) "." else "",
          separator = if (!showProjectPaths) ", " else ""
        ) { project ->
          (if (showProjectPaths) "<br />&nbsp;&nbsp;&nbsp;" else "") + "\"${project.name}\"" + (if (showProjectPaths) " (${project.basePath})" else "")
        }
    })
  }

  /**
   * Returns a list of open projects that were snoozed indefinitely.
   */
  private fun getSnoozedProjects(): List<Project> {
    val lastAutoSyncSettingChangeTimestamp = AutoSyncSettingStore.lastAutoSyncBehaviorChangeTimestamp() ?: 0L
    return ProjectManager.getInstance().openProjects.filter { project ->
      val snoozedAtOrNull = PropertiesComponent.getInstance(project).getValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP)?.toLong() ?: 0L
      snoozedAtOrNull > lastAutoSyncSettingChangeTimestamp
    }
  }
}