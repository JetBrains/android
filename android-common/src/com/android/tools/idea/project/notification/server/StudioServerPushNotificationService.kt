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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.StudioPushNotification
import com.android.tools.idea.serverflags.protos.StudioPushNotificationList
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ServerPushNotificationEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class StudioServerPushNotificationService(
  private val project: Project,
  private val scope: CoroutineScope,
  private val serverNotificationStoreProvider: () -> ServerNotificationStore,
  private val bridgesProvider: () -> List<ServerPushNotificationBridge>,
) : ServerPushNotificationListener {

  @Suppress("Unused")
  constructor(
    project: Project,
    scope: CoroutineScope,
  ) : this(
    project = project,
    scope = scope,
    serverNotificationStoreProvider = { ServerNotificationStore.getInstance() },
    bridgesProvider = { ServerPushNotificationBridge.getBridges(project) },
  )

  fun schedule() {
    scope.launch {
      delay(NOTIFICATION_DELAY)
      while (isActive) {
        showNotifications()
        delay(NOTIFICATION_POLLING_DELAY)
      }
    }
  }

  internal fun showNotifications(clock: Clock = Clock.System) {
    val notifications = fetchConfiguredNotifications()
    if (notifications.isEmpty()) return

    val eligibleNotifications = findEligibleNotifications(clock, notifications)

    eligibleNotifications.forEach { notification ->
      val id = notification.notificationIdentifier ?: return@forEach
      val type =
        if (notification.hasGotItTooltipSpec()) {
          ServerPushNotificationEvent.NotificationType.GOT_IT_TOOLTIP_NOTIFICATION
        } else {
          ServerPushNotificationEvent.NotificationType.BALLOON_NOTIFICATION
        }
      logAnalyticsEvent(id, type, ServerPushNotificationEvent.EventType.IS_ELIGIBLE)
    }

    if (isNotificationCooldownActive(clock)) {
      logger.debug("A push notification was already shown in the last 30 days.")
      return
    }

    findAndShowCandidate(clock, eligibleNotifications)
  }

  override fun onNotificationShown(notificationId: String) {
    logAnalyticsEvent(notificationId, ServerPushNotificationEvent.NotificationType.BALLOON_NOTIFICATION, ServerPushNotificationEvent.EventType.SHOWN)
  }

  override fun onNotificationDismissed(notificationId: String) {
    logAnalyticsEvent(notificationId, ServerPushNotificationEvent.NotificationType.BALLOON_NOTIFICATION, ServerPushNotificationEvent.EventType.DISMISSED)
  }

  override fun onNotificationActionClicked(notificationId: String, actionTitle: String) {
    logAnalyticsEvent(notificationId, ServerPushNotificationEvent.NotificationType.BALLOON_NOTIFICATION, ServerPushNotificationEvent.EventType.ACTION_CLICKED, actionTitle)
  }

  private fun fetchConfiguredNotifications(): List<StudioPushNotification> {
    val flagList =
      ServerFlagService.instance.getProtoOrNull(
        "$STUDIO_NOTIFICATIONS_FLAGS_DIR_NAME/$STUDIO_NOTIFICATION_LIST_FLAG_NAME",
        StudioPushNotificationList.getDefaultInstance(),
      )
    if (flagList == null) {
      logger.debug("Unable to extract the flag list.")
      return emptyList()
    }

    val emptyStudioPushNotification = StudioPushNotification.getDefaultInstance()
    return flagList.notificationsList.mapNotNull { notification ->
      val notificationDetails =
        ServerFlagService.instance.getProtoOrNull(
          "$STUDIO_NOTIFICATIONS_FLAGS_DIR_NAME/$notification",
          emptyStudioPushNotification,
        )
      if (notificationDetails == null) {
        logger.debug("Unable to find server side flag for notification: $notification")
        return@mapNotNull null
      }
      val notificationIdentifier = notificationDetails.notificationIdentifier
      if (notificationIdentifier.isNullOrBlank()) {
        logger.warn("Notification identifier is null")
        return@mapNotNull null
      }
      notificationDetails
    }
  }

  private fun findEligibleNotifications(clock: Clock, notifications: List<StudioPushNotification>): List<StudioPushNotification> {
    val store = serverNotificationStoreProvider()
    val eligible = notifications.filter { notification ->
      val id = notification.notificationIdentifier
      if (id.isNullOrBlank()) return@filter false

      if (store.isNotificationShown(id)) {
        logger.debug("Push notification with id: $id already shown once.")
        return@filter false
      }
      areAllRequirementsFulfilled(notification.requirementsList)
    }
    return eligible.sortedBy { it.presentationPriorityWeight }
  }

  private fun isNotificationCooldownActive(clock: Clock): Boolean {
    val lastShown = serverNotificationStoreProvider().getLastServerPushNotificationTimestamp()
    return lastShown != null && (clock.now() - lastShown).inWholeDays < 30
  }

  private fun findAndShowCandidate(clock: Clock, eligibleNotifications: List<StudioPushNotification>) {
    for (notification in eligibleNotifications) {
      val notificationIdentifier = notification.notificationIdentifier
      if (notificationIdentifier.isNullOrBlank()) {
        logger.warn("Notification identifier is null")
        continue
      }

      if (canShowNotification(clock, notificationIdentifier, notification)) {
        val shown = bridgesProvider().any { bridge ->
          bridge.showNotification(notification, listener = this)
        }
        if (shown) {
          serverNotificationStoreProvider().markNotificationAsShown(notificationIdentifier, clock)
          return
        }
      }
    }
  }

  private fun logAnalyticsEvent(
    notificationId: String,
    notificationType: ServerPushNotificationEvent.NotificationType,
    eventType: ServerPushNotificationEvent.EventType,
    actionTitle: String = "",
  ) {
    val event =
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.SERVER_PUSH_NOTIFICATION_EVENT)
        .setServerPushNotificationEvent(
          ServerPushNotificationEvent.newBuilder()
            .setNotificationId(notificationId)
            .setNotificationType(notificationType)
            .setEventType(eventType)
            .setActionTitle(actionTitle)
        )
    UsageTracker.log(event)
  }

  private val StudioPushNotification.notificationIdentifier: String?
    get() = when {
      hasGotItTooltipSpec() && gotItTooltipSpec.id.isNotBlank() -> gotItTooltipSpec.id
      hasNotificationSpec() && notificationSpec.id.isNotBlank() -> notificationSpec.id
      else -> null
    }

  private val StudioPushNotification.presentationPriorityWeight: Int
    get() = when (priority) {
      StudioPushNotification.Priority.P0 -> 1
      StudioPushNotification.Priority.P1 -> 2
      StudioPushNotification.Priority.P2 -> 3
      else -> 4
    }

  private fun canShowNotification(
    clock: Clock = Clock.System,
    notificationIdentifier: String,
    notificationDetails: StudioPushNotification?,
  ): Boolean {
    if (isAlreadyRendered(notificationIdentifier)) return false
    return areAllRequirementsFulfilled(notificationDetails?.requirementsList)
  }

  private fun areAllRequirementsFulfilled(requirements: List<StudioPushNotification.Requirement>?): Boolean {
    if (requirements.isNullOrEmpty()) return true

    return requirements.all { requirement ->
      val result = bridgesProvider().firstNotNullOfOrNull { it.checkRequirement(requirement) }
      if (result == null) {
        logger.debug("Requirement not handled by any bridge: $requirement")
        false
      } else {
        result
      }
    }
  }

  private fun isAlreadyRendered(notificationIdentifier: String): Boolean {
    if (serverNotificationStoreProvider().isNotificationShown(notificationIdentifier)) {
      logger.debug("Push notification with id: $notificationIdentifier already shown once.")
      return true
    }
    return false
  }

  companion object {
    @TestOnly var logForTesting: Logger? = null
    private val logger: Logger
      get() = logForTesting ?: thisLogger()

    private val NOTIFICATION_DELAY = 2.minutes
    private val NOTIFICATION_POLLING_DELAY = 1.days

    private const val STUDIO_NOTIFICATIONS_FLAGS_DIR_NAME = "studio_flags/studiobot_push_notifications"
    private const val STUDIO_NOTIFICATION_LIST_FLAG_NAME = "notification_flag_list"

    fun getInstance(project: Project): StudioServerPushNotificationService = project.service()
  }
}
