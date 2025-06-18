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

import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val PROP_KEY = "com.android.tools.idea.gservices.deprecation.last.date.checked"

class StudioDeprecationCheckerTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  private val notifications: List<Notification>
    get() =
      NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, projectRule.project)
        .toList()

  private lateinit var deprecationData: DevServicesDeprecationData

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
    deprecationData =
      DevServicesDeprecationData(
        "header",
        "description",
        "moreInfoUrl",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
        null,
      )
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
    assertThat(notification.title)
      .isEqualTo("Cloud services won't be accessible after ${deprecationData.formattedDate()}")
    assertThat(notification.content)
      .isEqualTo("Please update Android Studio to ensure uninterrupted access to cloud services.")
  }

  @Test
  fun testNotificationWhenDateLessThanThresholdWhenUnsupported() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)

    checker.execute(projectRule.project)

    assertThat(notifications).isNotEmpty()
    val notification = notifications.first()

    assertThat(notification.icon).isEqualTo(AllIcons.General.Error)
    assertThat(notification.type).isEqualTo(NotificationType.ERROR)
    assertThat(notification.title).isEqualTo("Unsupported Android Studio version")
    assertThat(notification.content)
      .isEqualTo("This version of Android Studio is no longer compatible with cloud services.")
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

  @Test
  fun testEventTrackedWhenNotificationShown() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    checker.execute(projectRule.project)
    assertThat(notifications).isNotEmpty()

    val shownEvent =
      usageTrackerRule.usages.first {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT
      }
    with(shownEvent.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo) {
      assertThat(deprecationStatus)
        .isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.UNSUPPORTED)
      assertThat(deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.NOTIFICATION)
      assertThat(userNotified).isTrue()
    }
  }

  @Test
  fun testEventTrackedWhenUpdateClicked() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    checker.execute(projectRule.project)
    assertThat(notifications).isNotEmpty()
    val notification = notifications.first()
    (notification.actions.first() as NotificationAction).actionPerformed(
      TestActionEvent.createTestEvent(),
      notification,
    )

    val updateEvent =
      usageTrackerRule.usages.last {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT
      }
    with(updateEvent.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo) {
      assertThat(deprecationStatus)
        .isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.UNSUPPORTED)
      assertThat(deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.NOTIFICATION)
      assertThat(updateClicked).isTrue()
    }
  }

  @Test
  fun testEventTrackedWhenMoreInfoClicked() = runTest {
    deprecationData = deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    checker.execute(projectRule.project)
    assertThat(notifications).isNotEmpty()
    val notification = notifications.first()
    (notification.actions.last() as NotificationAction).actionPerformed(
      TestActionEvent.createTestEvent(),
      notification,
    )

    val moreInfoEvent =
      usageTrackerRule.usages.last {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT
      }
    with(moreInfoEvent.studioEvent.studioDeprecationNotificationEvent.devServiceDeprecationInfo) {
      assertThat(deprecationStatus)
        .isEqualTo(DevServiceDeprecationInfo.DeprecationStatus.UNSUPPORTED)
      assertThat(deliveryType).isEqualTo(DevServiceDeprecationInfo.DeliveryType.NOTIFICATION)
      assertThat(moreInfoClicked).isTrue()
    }
  }
}
