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
package com.android.tools.idea.adblib

import com.android.adblib.DeviceConnectionType
import com.android.adblib.deviceInfo
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Monitor device connection event. If the device is using USB and the
 * negotiated speed is below the max capable speed, we issue a notification.
 */
class DeviceCableMonitor : ProjectActivity {

  private val NOTIFICATION_GROUP_ID = "Android Device Speed Warning"

  private val notificationGroup: NotificationGroup
    get() = NotificationGroup.findRegisteredGroup(NOTIFICATION_GROUP_ID)
            ?: NotificationGroup(
              NOTIFICATION_GROUP_ID,
              NotificationDisplayType.BALLOON,
            )

  override suspend fun execute(project: Project) {
    val service = project.service<DeviceProvisionerService>()
    service.deviceProvisioner.devices.map {
      it.toSet()
    }.trackSetChanges().collect { change ->
      when (change) {
        is SetChange.Add -> {
          // We don't want to run the coroutine on emulators or datacenter devices
          val properties = change.value.state.properties
          // isRemote can be null, we need to revert the test to pass anyway.
          if (properties.isVirtual != true && properties.isRemote != true) {
            change.value.scope.launch { monitorDevice(project, change.value) }
          }
        }

        is SetChange.Remove -> {}
      }
    }
  }

  private suspend fun monitorDevice(project: Project, handle: DeviceHandle) {
    val deviceInfo = handle.stateFlow.mapNotNull { it.connectedDevice?.deviceInfo }.first { it.connectionType == DeviceConnectionType.USB }

    // Some older devices have USB controller bugs where they report being able to do USB 3 while
    // only being USB 2 capable. We filter them out via the API level since which they are likely to not
    // have been updated.
    if (!handle.state.properties.androidVersion!!.isGreaterOrEqualThan(11)) {
      return
    }

    val deviceTitle = handle.state.properties.title
    val maxSpeed = deviceInfo.maxSpeed ?: return
    val negotiatedSpeed = deviceInfo.negotiatedSpeed ?: return
    val connectedDevice = handle.stateFlow.value.connectedDevice ?: return

    val isStudioNotificationEnabled = StudioFlags.ALERT_UPON_DEVICE_SUBOPTIMAL_SPEED.get()

    val notification = createNotification("'$deviceTitle' is capable of faster USB connectivity." +
           " Upgrade the cable/hub from ${ speedToString(negotiatedSpeed) } to ${speedToString(maxSpeed)}.")
    var notificationShown = false
    if (negotiatedSpeed == 480L && negotiatedSpeed < maxSpeed) {
      if (isStudioNotificationEnabled) {
        Notifications.Bus.notify(notification, project)
        notificationShown = true
      }
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ADB_DEVICE_CONNECTED)
        .setDeviceConnected(DeviceConnectedNotificationEvent.newBuilder()
                              .setType(com.google.wireless.android.sdk.stats.DeviceConnectedNotificationEvent.DeviceConnectionType.USB)
                              .setMaxSpeedMbps(maxSpeed)
                              .setNegotiatedSpeedMbps(negotiatedSpeed)
                              .setSpeedNotificationsStudioDisabled(!isStudioNotificationEnabled)
                              .setSpeedNotificationsUserDisabled(!notification.canShowFor(project))
        )
    )

    // Dismiss the notification once the device is unplugged or when project is closed.
    if (notificationShown && notification.canShowFor(project)) {
      withContext(NonCancellable) { // To avoid a race condition we cannot run on the DeviceHandle scope.
        val waiter = launch {handle.awaitRelease(connectedDevice)}
        // Notifications are expired when the project is closed
        notification.whenExpired {
          waiter.cancel()
        }
        waiter.join()
        notification.expire()
      }
    }
  }

  private fun createNotification(text: String = ""): Notification {
    return notificationGroup
      .createNotification(text, NotificationType.WARNING)
      .setTitle("Connection speed warning")
      .addAction(BrowseNotificationAction("Learn more", "https://d.android.com/r/studio-ui/usb-check"))
      .setImportant(true)
  }

  private fun speedToString(speed: Long): String {
    return when (speed) {
      1L -> "USB 1 (1 Mbps)"
      12L -> "USB 2 (12 Mbps)"
      480L -> "USB 2 (480 Mbps)"
      5000L -> "USB 3.0 (5,000 Mbps)"
      10000L -> "USB 3.1 (10,000 Mbps)"
      20000L -> "USB 3.2 (20,000 Mbps)"
      40000L -> "USB 4.0 (40,000 Mbps)"
      else -> "%,d Mbps".format(speed)
    }
  }
}