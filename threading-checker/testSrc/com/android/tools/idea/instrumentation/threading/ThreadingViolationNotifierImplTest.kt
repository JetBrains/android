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

import com.google.common.truth.Truth
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import java.util.Collections

internal class ThreadingViolationNotifierImplTest : LightPlatformTestCase() {
  private val notifications: MutableList<Notification> = Collections.synchronizedList(mutableListOf())

  override fun setUp() {
    super.setUp()

    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Threading Violation Notification") {
          notifications += notification
        }
      }
    })
    notifications.clear()
  }

  @Test
  fun testNotify() {
    ThreadingViolationNotifierImpl().notify("Threading problem", "ClassA#method1")
    UIUtil.dispatchAllInvocationEvents()
    Truth.assertThat(notifications).hasSize(1)
    Truth.assertThat(notifications[0].content).isEqualTo("Threading problem<p>Violating method: ClassA#method1")
    Truth.assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
  }
}