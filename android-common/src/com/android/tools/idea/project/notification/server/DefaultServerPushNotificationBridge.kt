/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.project.notification.server

import com.android.tools.idea.serverflags.protos.StudioPushNotification
import com.android.tools.idea.serverflags.protos.StudioPushNotification.NotificationSpec
import com.android.tools.idea.serverflags.protos.StudioPushNotification.NotificationSpec.PushNoficationType
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.DateCondition
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.FeatureUsage
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlin.time.Clock
import kotlin.time.Instant

private const val STUDIO_SERVER_PUSH_NOTIFICATION_GROUP_ID = "Studio Server Push Notifications"

private val NOTIFICATION_TYPE_MAP =
  mapOf(
    PushNoficationType.PUSH_NOTIFICATION_TYPE_UNKNOWN to NotificationType.INFORMATION,
    PushNoficationType.INFORMATION to NotificationType.INFORMATION,
    PushNoficationType.WARNING to NotificationType.WARNING,
    PushNoficationType.ERROR to NotificationType.ERROR,
  )

private fun NotificationSpec.getNotificationType(): NotificationType =
  NOTIFICATION_TYPE_MAP[pushNotificationType] ?: NotificationType.INFORMATION

/**
 * A default implementation of [ServerPushNotificationBridge]. Natively handles standard Balloon notifications alongside other custom bridge
 * implementations.
 */
internal class DefaultServerPushNotificationBridge(
  private val project: Project,
  private val serverNotificationStoreProvider: () -> ServerNotificationStore,
) : ServerPushNotificationBridge {

  @Suppress("Unused")
  constructor(
    project: Project
  ) : this(
    project = project,
    serverNotificationStoreProvider = { ServerNotificationStore.getInstance() },
  )

  override fun checkRequirement(requirement: Requirement): Boolean? {
    return when {
      requirement.hasDateCondition() -> checkDateCondition(requirement.dateCondition)
      requirement.hasFeatureUsage() && requirement.featureUsage.hasDaysSinceLastUse() -> checkDaysSinceLastUse(requirement.featureUsage)
      else -> null
    }
  }

  private fun checkDaysSinceLastUse(featureUsage: FeatureUsage): Boolean {
    val lastUsedInstant = serverNotificationStoreProvider().getLastUsedTimestamp(featureUsage.feature.number) ?: return true
    return (Clock.System.now() - lastUsedInstant).inWholeDays > featureUsage.daysSinceLastUse
  }

  private fun checkDateCondition(dateCondition: DateCondition): Boolean {
    val now = Clock.System.now()
    if (dateCondition.hasShownAfter() && now < Instant.fromEpochSeconds(dateCondition.shownAfter.seconds)) {
      return false
    }
    if (dateCondition.hasShownBefore() && now > Instant.fromEpochSeconds(dateCondition.shownBefore.seconds)) {
      return false
    }
    return true
  }

  override fun showNotification(notification: StudioPushNotification, listener: ServerPushNotificationListener): Boolean {
    if (notification.hasNotificationSpec() && notification.notificationSpec.id.isNotBlank()) {
      showBalloonNotification(notification.notificationSpec, listener)
      return true
    }
    return false
  }

  private fun showBalloonNotification(notificationSpec: NotificationSpec, listener: ServerPushNotificationListener) {
    val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(STUDIO_SERVER_PUSH_NOTIFICATION_GROUP_ID)
    if (notificationGroup == null) {
      logger.warn("Notification group $STUDIO_SERVER_PUSH_NOTIFICATION_GROUP_ID is not registered.")
      return
    }
    val notification =
      notificationGroup.createNotification(notificationSpec.title, notificationSpec.description, notificationSpec.getNotificationType())

    notification.whenExpired {
      listener.onNotificationDismissed(notificationSpec.id)
    }

    notificationSpec.actionsList.forEach { action ->
      if (action.title.isNullOrBlank()) {
        logger.warn("Skipping action because action title is missing.")
      } else if (action.hasBrowseAction()) {
        notification.addAction(
          object : NotificationAction(action.title) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              try {
                listener.onNotificationActionClicked(notificationSpec.id, action.title)
                if (!ApplicationManager.getApplication().isUnitTestMode) {
                  BrowserUtil.browse(action.browseAction.url)
                }
              } catch (exp: Exception) {
                logger.warn("Unable to trigger URL", exp)
              }
            }
          }
        )
      } else if (action.hasUiScreenId()) {
        val actionId = action.uiScreenId
        val dataContextParams = action.dataContextParamsMap
        notification.addAction(
          object : NotificationAction(action.title) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
              listener.onNotificationActionClicked(notificationSpec.id, action.title)
              executeIdeAction(project, actionId, dataContextParams)
              notification.expire()
            }
          }
        )
      }
    }

    listener.onNotificationShown(notificationSpec.id)
    notification.notify(project)
  }

  companion object {
    private val logger = thisLogger()
  }
}
