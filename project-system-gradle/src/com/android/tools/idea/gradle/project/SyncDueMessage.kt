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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent
import com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent.ChangeSource
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.IndicatorType
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.UserAction
import com.google.wireless.android.sdk.stats.SuppressedSyncEvent.newBuilder
import com.intellij.icons.AllIcons
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
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.android.util.AndroidBundle

@VisibleForTesting
const val SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID = "Syncing project is recommended"

const val SYNC_DUE_SNOOZED_SETTING = "gradle.settings.autoSync.notification.snoozed"
const val SYNC_DUE_DIALOG_SHOWN = "gradle.settings.autoSync.dialog.shown"
const val SNOOZED_DURATION_MILLIS = 30 * 60 * 1000

object SyncDueMessage {

  private val LOG = Logger.getInstance(SyncDueMessage::class.java)

  /**
   * Checks if Sync Due messages are snoozed, and chooses between dialog and notification
   * depending on showing for the first time.
   */
  fun maybeShow(project: Project): Boolean {
    return when {
      isSnoozed() -> {
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
    AndroidBundle.message("gradle.settings.autoSync.due.message", ApplicationNamesInfo.getInstance().fullProductName),
    NotificationType.WARNING,
  ) {
    private var actionSelected = false

    init {
      NotificationsConfiguration.getNotificationsConfiguration().setDisplayType(SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID,
                                                                                NotificationDisplayType.STICKY_BALLOON)
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.due.action.sync")) {
        override fun actionPerformed(e: AnActionEvent) {
          actionSelected = true
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.SINGLE_SYNC)
          requestProjectSync(project)
          ActivityTracker.getInstance().inc()
          hideBalloon()
        }
      })
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.due.action.enableAutoSync")) {
        override fun actionPerformed(e: AnActionEvent) {
          actionSelected = true
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.ENABLE_AUTO_SYNC)
          trackAutoSyncEnabled(changeSource = ChangeSource.NOTIFICATION)
          GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Default
          requestProjectSync(project)
          ActivityTracker.getInstance().inc()
          hideBalloon()
        }
      })
      addAction(object : AnAction(AndroidBundle.message("gradle.settings.autoSync.due.action.snooze")) {
        override fun actionPerformed(e: AnActionEvent) {
          actionSelected = true
          trackSuppressedSync(type = IndicatorType.NOTIFICATION, action = UserAction.SNOOZE)
          snooze()
          hideBalloon()
        }
      })
    }

    override fun setBalloon(balloon: Balloon) {
      super.setBalloon(balloon)
      balloon.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          super.onClosed(event)
          expire()
          if (actionSelected) return
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
        val dialogSelection = MessageDialogBuilder
          .yesNoCancel(
            AndroidBundle.message("gradle.settings.autoSync.dialog.title"),
            AndroidBundle.message("gradle.settings.autoSync.dialog.message", ApplicationNamesInfo.getInstance().fullProductName)
          )
          .icon(AllIcons.General.WarningDialog)
          .doNotAsk(object : DoNotAskOption {
            override fun isToBeShown(): Boolean = false

            override fun setToBeShown(toBeShown: Boolean, exitCode: Int) {
              if (!toBeShown && exitCode == OK_EXIT_CODE) {
                trackAutoSyncEnabled(changeSource = ChangeSource.DIALOG)
                GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Default
              }
            }

        override fun canBeHidden(): Boolean = true

            override fun shouldSaveOptionsOnCancel(): Boolean = false

            override fun getDoNotShowMessage(): @NlsContexts.Checkbox String = AndroidBundle.message(
              "gradle.settings.autoSync.dialog.enable")

          })
          .yesText(AndroidBundle.message("gradle.settings.autoSync.dialog.sync"))
          .noText(AndroidBundle.message("gradle.settings.autoSync.dialog.continue"))
          .cancelText(AndroidBundle.message("gradle.settings.autoSync.dialog.snooze"))
          .show(project)

        when (dialogSelection) {
          MessageConstants.YES -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.SINGLE_SYNC)
            requestProjectSync(project)
          }

          MessageConstants.CANCEL -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.SNOOZE)
            snooze()
          }

          MessageConstants.NO -> {
            trackSuppressedSync(type = IndicatorType.DIALOG, action = UserAction.CLOSED)
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

  private fun isShownFirstTime(): Boolean {
    return PropertiesComponent.getInstance().getValue(SYNC_DUE_DIALOG_SHOWN).isNullOrBlank()
  }

  private fun markAsShownDialog() {
    PropertiesComponent.getInstance().setValue(SYNC_DUE_DIALOG_SHOWN, true)
  }

  private fun isSnoozed(): Boolean {
    val snoozedAt = PropertiesComponent.getInstance().getValue(SYNC_DUE_SNOOZED_SETTING)
    return !snoozedAt.isNullOrBlank() && (System.currentTimeMillis() - snoozedAt.toLong()) < SNOOZED_DURATION_MILLIS
  }

  private fun snooze() {
    PropertiesComponent.getInstance().setValue(SYNC_DUE_SNOOZED_SETTING, System.currentTimeMillis().toString())
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
}