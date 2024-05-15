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

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceConnectionType
import com.android.adblib.DeviceState
import com.android.adblib.scope
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
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    service.deviceProvisioner.devices.collect { deviceHandle ->
      deviceHandle.forEach {
        it.scope.launch {
          collectDeviceState(project, it.stateFlow)
        }
      }
    }
  }

  private suspend fun collectDeviceState(project: Project, stateFlow: StateFlow<com.android.sdklib.deviceprovisioner.DeviceState>) {
    stateFlow.collect { deviceState ->
      deviceState.connectedDevice?.let {
        it.scope.launch {
          collectDeviceInfo(project, it)
        }
      }
    }
  }

  private suspend fun collectDeviceInfo(project: Project, connectedDevice: ConnectedDevice) {
    connectedDevice.deviceInfoFlow.collect {
      if (it.connectionType != DeviceConnectionType.USB || it.deviceState != DeviceState.ONLINE) {
        return@collect
      }
      val maxSpeed = it.maxSpeed ?: return@collect
      val negotiatedSpeed = it.negotiatedSpeed ?: return@collect

      val isStudioNotificationEnabled = StudioFlags.ALERT_UPON_DEVICE_SUBOPTIMAL_SPEED.get()

      if (negotiatedSpeed == 480L && negotiatedSpeed < maxSpeed) {
        if (isStudioNotificationEnabled) {
          showNotification(project,  "'${it.model}' is capable of faster USB connectivity. Upgrade the cable from ${speedToString(negotiatedSpeed)} to ${speedToString(maxSpeed)}.")
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
                                .setSpeedNotificationsUserDisabled(!createNotification().canShowFor(project))
          )
      )
    }
  }

  private fun showNotification(project: Project, text: String) {
    val notification = createNotification(text)
    notification.addAction(BrowseNotificationAction("Learn more", "https://d.android.com/r/studio-ui/usb-check"))

    Notifications.Bus.notify(notification, project)
  }

  private fun createNotification(text: String = ""): Notification {
    return notificationGroup
      .createNotification(text, NotificationType.WARNING)
      .setTitle("Connection speed warning")
      .setImportant(true)
  }

  private fun speedToString(speed: Long) : String {
    return when (speed) {
      1L -> "USB-1 (1 Mbps)"
      12L -> "USB-2 (12 Mbps)"
      480L -> "USB-2 (480 Mbps)"
      5000L -> "USB-3.0 (5,000 Mbps)"
      10000L -> "USB-3.1 (10,000 Mbps)"
      20000L -> "USB-3.2 (20,000 Mbps)"
      40000L -> "USB-4.0 (40,000 Mbps)"
      else -> "%,d Mbps".format(speed)
    }
  }
}