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

import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.concurrency.createCoroutineScope
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
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
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
        .filter { it.groupId == "StudioDeprecationNotification" }
        .toList()

  private lateinit var deprecationData: DevServicesDeprecationData
  private lateinit var scope: CoroutineScope
  private lateinit var checker: StudioDeprecationChecker

  private val deprecatedDataFlow = MutableStateFlow(DevServicesDeprecationData.EMPTY)
  private val service =
    object : DevServicesDeprecationDataProvider {
      override fun getCurrentDeprecationData(serviceName: String, userFriendlyServiceName: String) =
        deprecatedDataFlow.value

      override fun registerServiceForChange(
        serviceName: String,
        userFriendlyServiceName: String,
        disposable: Disposable,
      ) = deprecatedDataFlow.asStateFlow()
    }

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
    scope = projectRule.project.createCoroutineScope()
    checker = StudioDeprecationChecker(scope)
  }

  @After
  fun teardown() {
    notifications.forEach { it.expire() }
  }

  @Test
  fun testNoNotificationWhenSupported() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.SUPPORTED)
    }

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNoNotificationWhenDateNotAvailableInDeprecated() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.SUPPORTED)
    }
    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNoNotificationWhenDateMoreThanThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(40)
    deprecatedDataFlow.update { deprecationData.copy(date = future) }

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testNotificationWhenDateEqualToThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(30)
    deprecatedDataFlow.update { deprecationData.copy(date = future) }

    delayUntilCondition(200) { notifications.isNotEmpty() }
  }

  @Test
  fun testNotificationWhenDateLessThanThresholdWhenDeprecated() = runTest {
    val future = LocalDate.now().plusDays(15)
    deprecatedDataFlow.update { deprecationData.copy(date = future) }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()

    assertThat(notification.icon).isEqualTo(AllIcons.General.Warning)
    assertThat(notification.type).isEqualTo(NotificationType.WARNING)
    assertThat(notification.title)
      .isEqualTo(
        "Cloud services won't be accessible after ${deprecatedDataFlow.value.formattedDate()}"
      )
    assertThat(notification.content)
      .isEqualTo("Please update Android Studio to ensure uninterrupted access to cloud services.")
  }

  @Test
  fun testNotificationWhenDateLessThanThresholdWhenUnsupported() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

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
    deprecatedDataFlow.update { deprecationData.copy(date = now) }
    delayUntilCondition(200) { notifications.isNotEmpty() }

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
    deprecatedDataFlow.update { deprecationData.copy(date = now) }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()

    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, "")).isEmpty()
    val actions = notification.actions.filterIsInstance<NotificationAction>()

    // Update
    actions[0].actionPerformed(TestActionEvent.createTestEvent(), notification)
    delayUntilCondition(200) { PropertiesComponent.getInstance().getValue(PROP_KEY, "") != "" }
    assertThat(PropertiesComponent.getInstance().getValue(PROP_KEY, ""))
      .isEqualTo(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
  }

  @Test
  fun testNoNotificationWhenPropDateMatchesDeprecationDate() = runTest {
    val now = LocalDate.now()
    PropertiesComponent.getInstance()
      .setValue(PROP_KEY, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    deprecatedDataFlow.update { deprecationData.copy(date = now) }

    assertThat(notifications).isEmpty()
  }

  @Test
  fun testEventTrackedWhenNotificationShown() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    waitForCondition(2.seconds) {
      usageTrackerRule.usages.firstOrNull {
        it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT
      } != null
    }

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
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

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
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

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

  @Test
  fun testNotificationExpiredWhenStatusChangesFromUNSUPPORTEDtoSUPPORTED() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.SUPPORTED)
    }

    delayUntilCondition(200) { notification.isExpired }
  }

  @Test
  fun testNotificationExpiredWhenStatusChangesFromDEPRECATEDtoSUPPORTED() = runBlocking {
    deprecatedDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "description",
        "moreInfoUrl",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
        LocalDate.now().plusDays(5),
      )
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.SUPPORTED)
    }

    delayUntilCondition(200) { notification.isExpired }
  }

  @Test
  fun testNotificationExpiredWhenStatusChangesFromDEPRECATEDtoUNSUPPORTED() = runTest {
    deprecatedDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "description",
        "moreInfoUrl",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
        LocalDate.now().plusDays(5),
      )
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()
    assertThat(notification.icon).isEqualTo(AllIcons.General.Warning)
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notification.isExpired }

    delayUntilCondition(200) { notifications.firstOrNull() != notification }
    delayUntilCondition(200) { notifications.firstOrNull() != null }
    val notification2 = notifications.first()
    assertThat(notification2.icon).isEqualTo(AllIcons.General.Error)
  }

  @Test
  fun testNotificationExpiredWhenStatusChangesFromUNSUPPORTEDtoDEPRECATED() = runTest {
    deprecatedDataFlow.update {
      deprecationData.copy(status = DevServicesDeprecationStatus.UNSUPPORTED)
    }
    delayUntilCondition(200) { notifications.isNotEmpty() }

    val notification = notifications.first()
    assertThat(notification.icon).isEqualTo(AllIcons.General.Error)
    deprecatedDataFlow.update {
      DevServicesDeprecationData(
        "header",
        "description",
        "moreInfoUrl",
        true,
        DevServicesDeprecationStatus.DEPRECATED,
        LocalDate.now().plusDays(5),
      )
    }

    delayUntilCondition(200) { notification.isExpired }

    delayUntilCondition(200) { notifications.firstOrNull() != notification }
    delayUntilCondition(200) { notifications.firstOrNull() != null }
    val notification2 = notifications.first()
    assertThat(notification2.icon).isEqualTo(AllIcons.General.Warning)
  }
}
