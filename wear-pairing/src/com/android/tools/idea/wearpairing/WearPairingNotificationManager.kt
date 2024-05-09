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
package com.android.tools.idea.wearpairing

import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.VisibleForTesting

private const val WEAR_PAIRING_NOTIFICATION_GROUP_ID = "Wear Pairing"

interface WearPairingNotificationManager {
  fun showReconnectMessageBalloon(
    phoneWearPair: WearPairingManager.PhoneWearPair,
    wizardAction: WizardAction?,
  )

  fun showConnectionDroppedBalloon(
    offlineName: String,
    phoneWearPair: WearPairingManager.PhoneWearPair,
    wizardAction: WizardAction?,
  )

  fun dismissNotifications(phoneWearPair: WearPairingManager.PhoneWearPair)

  companion object {
    private val instance = WearPairingNotificationManagerImpl()

    fun getInstance(): WearPairingNotificationManager = instance
  }
}

@VisibleForTesting
class WearPairingBalloonNotification(
  title: String,
  content: String,
  val phoneWearPair: WearPairingManager.PhoneWearPair,
  wizardAction: WizardAction?,
) : Notification(WEAR_PAIRING_NOTIFICATION_GROUP_ID, title, content, NotificationType.INFORMATION) {
  init {
    addAction(
      NotificationAction.create(
        AndroidWearPairingBundle.message("wear.assistant.device.connection.balloon.link")
      ) { action, notification ->
        notification.expire()
        wizardAction?.restart(action.project)
      }
    )
  }
}

class WearPairingNotificationManagerImpl @VisibleForTesting constructor() :
  WearPairingNotificationManager {

  @get:VisibleForTesting
  val pendingNotifications: List<WearPairingBalloonNotification>
    get() =
      ProjectManager.getInstance()
        .openProjects
        .flatMap {
          NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(WearPairingBalloonNotification::class.java, it)
            .toList()
        }
        .filter { !it.isExpired }

  override fun showReconnectMessageBalloon(
    phoneWearPair: WearPairingManager.PhoneWearPair,
    wizardAction: WizardAction?,
  ) {
    dismissNotifications(phoneWearPair)
    showMessageBalloon(
      AndroidWearPairingBundle.message("wear.assistant.device.connection.reconnected.title"),
      AndroidWearPairingBundle.message(
        "wear.assistant.device.connection.reconnected.message",
        phoneWearPair.wear.displayName,
        phoneWearPair.phone.displayName,
      ),
      phoneWearPair,
      wizardAction,
    )

    WearPairingUsageTracker.log(WearPairingEvent.EventKind.AUTOMATIC_RECONNECT)
  }

  override fun showConnectionDroppedBalloon(
    offlineName: String,
    phoneWearPair: WearPairingManager.PhoneWearPair,
    wizardAction: WizardAction?,
  ) {
    dismissNotifications(phoneWearPair)

    showMessageBalloon(
      AndroidWearPairingBundle.message("wear.assistant.device.connection.dropped.title"),
      AndroidWearPairingBundle.message(
        "wear.assistant.device.connection.dropped.message",
        offlineName,
        phoneWearPair.wear.displayName,
        phoneWearPair.phone.displayName,
      ),
      phoneWearPair,
      wizardAction,
    )
  }

  private fun showMessageBalloon(
    title: String,
    text: String,
    phoneWearPair: WearPairingManager.PhoneWearPair,
    wizardAction: WizardAction?,
  ) {
    ProjectManager.getInstance().openProjects.forEach {
      WearPairingBalloonNotification(title, "$text<br/>", phoneWearPair, wizardAction).notify(it)
    }
  }

  override fun dismissNotifications(phoneWearPair: WearPairingManager.PhoneWearPair) {
    pendingNotifications.filter { it.phoneWearPair == phoneWearPair }.forEach { it.expire() }
  }
}
