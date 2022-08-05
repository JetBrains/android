/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.instrumentation.threading

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager

internal class ThreadingViolationNotifierImpl : ThreadingViolationNotifier {
  override fun notify(warningMessage: String, methodSignature: String) {
    val notificationGroup =
      NotificationGroupManager.getInstance().getNotificationGroup("Threading Violation Notification")
    if (notificationGroup == null && ApplicationManager.getApplication().isUnitTestMode) {
      // Do not fail the tests if we cannot show notifications due to notificationGroup not being
      // found which happens if the 'threading-checker.xml' isn't included the test's plugin.xml.
      return
    }
    val notification =
      notificationGroup!!
        .createNotification(
          "Threading violation",
          "$warningMessage<p>Violating method: $methodSignature",
          NotificationType.ERROR
        )

    ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
  }
}
