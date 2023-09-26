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
package com.android.tools.idea.memorysettings

import com.google.common.truth.Truth.assertWithMessage
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.application
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

/** Tests [AndroidLowMemoryNotifier]. */
class AndroidLowMemoryNotifierTest {
  @get:Rule val appRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()

  @Test
  @RunsInEdt
  fun testNotification() {
    // Trigger low-memory conditions and watch for our notification.
    val notificationReceived = CompletableFuture<Boolean>()
    application.messageBus.connect(disposableRule.disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.displayId == AndroidLowMemoryNotifier.NOTIFICATION_DISPLAY_ID) {
          notificationReceived.complete(true)
        }
      }
    })
    LowMemoryWatcher.onLowMemorySignalReceived(true)
    notificationReceived.completeOnTimeout(false, 1, SECONDS)
    assertWithMessage("AndroidLowMemoryNotifier should send a notification").that(notificationReceived.get()).isTrue()
  }
}
