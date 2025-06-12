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
package com.android.tools.idea.gservices

import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PROP_KEY = "com.android.tools.idea.gservices.deprecation.last.date.checked"

class StudioDeprecationCheckerTest {
  @get:Rule val projectRule = ProjectRule()
  private val notifications: List<Notification>
    get() =
      NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, projectRule.project)
        .toList()

  private var deprecationData =
    DevServicesDeprecationData(
      "header",
      "description",
      "moreInfoUrl",
      true,
      DevServicesDeprecationStatus.DEPRECATED,
      null,
    )

  private val service =
    object : DevServicesDeprecationDataProvider {
      override fun getCurrentDeprecationData(serviceName: String, userFriendlyServiceName: String) =
        deprecationData
    }

  private val checker = StudioDeprecationChecker()

  @Before
  fun setup() {
    application.replaceService(
      DevServicesDeprecationDataProvider::class.java,
      service,
      projectRule.disposable,
    )
    PropertiesComponent.getInstance().unsetValue(PROP_KEY)
  }

  @Test
  fun testNoNotificationWhenSupported() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.SUPPORTED)
    checker.execute(projectRule.project)

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNoNotificationWhenDateNotAvailableInDeprecated() = runTest {
    checker.execute(projectRule.project)

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNoNotificationWhenDateMoreThanThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(40)
    deprecationData = deprecationData.copy(date = future)

    checker.execute(projectRule.project)

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNotificationWhenDateEqualToThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(30)
    deprecationData = deprecationData.copy(date = future)

    checker.execute(projectRule.project)

    assertThat(notifications).isNotEmpty()
  }

  @Test
  fun testNotificationWhenDateLessThanThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(15)
    deprecationData = deprecationData.copy(date = future)

    checker.execute(projectRule.project)

    assertThat(notifications).isNotEmpty()
    val notification = notifications.first()

    assertThat(notification.icon).isEqualTo(AllIcons.General.Warning)
    assertThat(notification.type).isEqualTo(NotificationType.WARNING)
    assertThat(notification.title).isEqualTo(deprecationData.header)
    assertThat(notification.content).isEqualTo(deprecationData.description)
  }

  @Test
  fun testNotificationWhenDateLessThanThresholdWhenUnsupported() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)

    checker.execute(projectRule.project)

    assertThat(notifications).isNotEmpty()
    val notification = notifications.first()

    assertThat(notification.icon).isEqualTo(AllIcons.General.Error)
    assertThat(notification.type).isEqualTo(NotificationType.ERROR)
    assertThat(notification.title).isEqualTo(deprecationData.header)
    assertThat(notification.content).isEqualTo(deprecationData.description)
  }

  @Test
  fun testNotificationDoesNotStorePropOnMoreInfo() = runTest {
    val now = LocalDate.now()
    deprecationData = deprecationData.copy(date = now)

    checker.execute(projectRule.project)
    assertThat(notifications).isNotEmpty()

    val notification = notifications.first()

    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, "")).isEmpty()
    val actions = notification.actions.filterIsInstance<NotificationAction>()

    // More info
    actions[1].actionPerformed(TestActionEvent.createTestEvent(), notification)
    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, "")).isEmpty()
  }

  @Test
  fun testNotificationStoresPropOnUpdate() = runTest {
    val now = LocalDate.now()
    deprecationData = deprecationData.copy(date = now)

    checker.execute(projectRule.project)
    assertThat(notifications).isNotEmpty()

    val notification = notifications.first()

    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, "")).isEmpty()
    val actions = notification.actions.filterIsInstance<NotificationAction>()

    // Update
    actions[0].actionPerformed(TestActionEvent.createTestEvent(), notification)
    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, ""))
      .isEqualTo(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
  }

  @Test
  fun testNoNotificationWhenPropDateMatchesDeprecationDate() = runTest {
    val now = LocalDate.now()
    PropertiesComponent.getInstance()
      .setValue(PROP_KEY, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    deprecationData = deprecationData.copy(date = now)

    checker.execute(projectRule.project)
    assertThat(notifications).isEmpty()
  }
}