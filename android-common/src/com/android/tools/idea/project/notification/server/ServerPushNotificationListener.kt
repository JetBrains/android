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

/**
 * Listener callback interface provided by [StudioServerPushNotificationService] to bridge renderers. Bridge renderers invoke these
 * callbacks when UI interactions occur on screen.
 */
interface ServerPushNotificationListener {
  /** Invoked when the notification is rendered on screen. */
  fun onNotificationShown(notificationId: String)

  /** Invoked when the notification is dismissed or closed by the user or expired. */
  fun onNotificationDismissed(notificationId: String)

  /** Invoked when the user clicks an action link or button in the notification. */
  fun onNotificationActionClicked(notificationId: String, actionTitle: String = "")
}
