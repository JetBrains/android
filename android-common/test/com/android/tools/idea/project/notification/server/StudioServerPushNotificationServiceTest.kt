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

import com.android.tools.idea.serverflags.FakeServerFlagService
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.StudioPushNotification
import com.android.tools.idea.serverflags.protos.StudioPushNotification.GotItTooltipSpec
import com.android.tools.idea.serverflags.protos.StudioPushNotification.NotificationSpec
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement
import com.android.tools.idea.serverflags.protos.StudioPushNotificationList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private class TestClock(epochMillis: Long = 0L) : Clock {
  private var currentInstant = Instant.fromEpochMilliseconds(epochMillis)

  override fun now(): Instant = currentInstant

  operator fun plusAssign(duration: Duration) {
    currentInstant += duration
  }
}

@RunWith(JUnit4::class)
class StudioServerPushNotificationServiceTest : LightIdeaTestCase() {

  private val testLogger: Logger = mock()
  private val clock = TestClock()
  private val serverFlagService = FakeServerFlagService()
  private val mockNotificationStore = ServerNotificationStore()
  private lateinit var service: StudioServerPushNotificationService

  private val checkedRequirements = CopyOnWriteArrayList<Requirement>()
  private val shownNotifications = CopyOnWriteArrayList<StudioPushNotification>()

  private lateinit var fakeBridge: ServerPushNotificationBridge

  override fun setUp() {
    super.setUp()

    fakeBridge = object : ServerPushNotificationBridge {
      override fun checkRequirement(requirement: Requirement): Boolean? {
        checkedRequirements.add(requirement)
        return true
      }
      override fun showNotification(notification: StudioPushNotification, listener: ServerPushNotificationListener): Boolean {
        shownNotifications.add(notification)
        val id = if (notification.hasGotItTooltipSpec()) notification.gotItTooltipSpec.id else notification.notificationSpec.id
        listener.onNotificationShown(id)
        return true
      }
    }

    ApplicationManager.getApplication().apply {
      replaceService(ServerFlagService::class.java, serverFlagService, testRootDisposable)
    }

    StudioServerPushNotificationService.logForTesting = testLogger
    service = createService()
  }

  private fun createService(): StudioServerPushNotificationService {
    return StudioServerPushNotificationService(
      project = project,
      scope = CoroutineScope(Dispatchers.Unconfined),
      serverNotificationStoreProvider = { mockNotificationStore },
      bridgesProvider = { listOf(fakeBridge) },
    )
  }

  @Test
  fun testCanShowNotification_notificationAlreadyShown_returnsFalse() = runTest {
    val notification = gotItTooltipWithAllData
    registerNotification(notification)
    mockNotificationStore.markNotificationAsShown(notification.gotItTooltipSpec.id)

    service.showNotifications(clock)
    assertThat(shownNotifications).isEmpty()
  }

  @Test
  fun testCanShowNotification_requirementNotFulfilled_returnsFalse() = runTest {
    val fakeFailingBridge = object : ServerPushNotificationBridge {
      override fun checkRequirement(requirement: Requirement): Boolean? = false
      override fun showNotification(notification: StudioPushNotification, listener: ServerPushNotificationListener): Boolean = true
    }
    val serviceWithFailingBridge = StudioServerPushNotificationService(
      project = project,
      scope = CoroutineScope(Dispatchers.Unconfined),
      serverNotificationStoreProvider = { mockNotificationStore },
      bridgesProvider = { listOf(fakeFailingBridge) },
    )
    val requirement = Requirement.newBuilder().build()
    val notification = gotItTooltipWithAllData.toBuilder().addRequirements(requirement).build()
    registerNotification(notification)

    serviceWithFailingBridge.showNotifications(clock)
    assertThat(shownNotifications).isEmpty()
  }

  @Test
  fun testCanShowNotification_happyScenario_returnsTrue() = runTest {
    val notification = balloonNotificationWithAllData
    registerNotification(notification)

    service.showNotifications(clock)
    assertThat(shownNotifications).hasSize(1)
  }

  @Test
  fun testShowNotification_nullNotificationIdentifier_notificationNotShown() = runTest {
    val notification = gotItTooltipMissingId
    registerNotification(notification)

    service.showNotifications(clock)
    UIUtil.dispatchAllInvocationEvents()

    verify(testLogger).warn("Notification identifier is null")
  }

  @Test
  fun testCustomRequirementChecker() = runTest {
    val fakeCustomBridge = object : ServerPushNotificationBridge {
      override fun checkRequirement(requirement: Requirement): Boolean? = true
      override fun showNotification(notification: StudioPushNotification, listener: ServerPushNotificationListener): Boolean = true
    }

    val serviceWithCustomBridge = StudioServerPushNotificationService(
      project = project,
      scope = CoroutineScope(Dispatchers.Unconfined),
      serverNotificationStoreProvider = { mockNotificationStore },
      bridgesProvider = { listOf(fakeCustomBridge) },
    )

    val customRequirement = Requirement.newBuilder().build()
    val notification = balloonNotificationWithAllData.toBuilder().addRequirements(customRequirement).build()
    registerNotification(notification)

    serviceWithCustomBridge.showNotifications(clock)
    assertThat(mockNotificationStore.isNotificationShown(notification.notificationSpec.id)).isTrue()
  }

  @Test
  fun testShowNotification_showsNotificationExactlyOnceAndMarksAsShown() = runTest {
    val notification = gotItTooltipWithAllData
    registerNotification(notification)
    assertThat(mockNotificationStore.isNotificationShown(notification.gotItTooltipSpec.id)).isFalse()
    assertThat(mockNotificationStore.getLastServerPushNotificationTimestamp()).isNull()

    service.showNotifications(clock)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(mockNotificationStore.isNotificationShown(notification.gotItTooltipSpec.id)).isTrue()
    assertThat(mockNotificationStore.getLastServerPushNotificationTimestamp()).isEqualTo(clock.now())

    service.showNotifications(clock)
    verify(testLogger).debug("A push notification was already shown in the last 30 days.")

    clock += 31.days
    service.showNotifications(clock)
    verify(testLogger, times(2)).debug("Push notification with id: ${notification.gotItTooltipSpec.id} already shown once.")
  }

  @Test
  fun testShowNotifications_multipleEligibleCampaigns_showsOnlyFirst() = runTest {
    val notification1 = gotItTooltipWithAllData
    val notification2 = gotItTooltipWithAllData.toBuilder()
      .setGotItTooltipSpec(
        gotItTooltipWithAllData.gotItTooltipSpec.toBuilder().setId("another_gotit_id")
      )
      .build()

    val notificationList = StudioPushNotificationList.newBuilder()
      .addNotifications("campaign_one")
      .addNotifications("campaign_two")
      .build()
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/notification_flag_list", notificationList)
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/campaign_one", notification1)
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/campaign_two", notification2)

    service.showNotifications(clock)

    assertThat(shownNotifications).hasSize(1)
    assertThat(shownNotifications.first().gotItTooltipSpec.id).isEqualTo(notification1.gotItTooltipSpec.id)
  }

  @Test
  fun testShowNotifications_selectsHighestPriorityCandidate() = runTest {
    val campaign1 = createNotificationWithPriority("BALLOON_NOTIFICATION_ONE", StudioPushNotification.Priority.P2)
    val campaign2 = createNotificationWithPriority("BALLOON_NOTIFICATION_TWO", StudioPushNotification.Priority.P0)

    val notificationList = StudioPushNotificationList.newBuilder()
      .addNotifications("campaign_one")
      .addNotifications("campaign_two")
      .build()
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/notification_flag_list", notificationList)
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/campaign_one", campaign1)
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/campaign_two", campaign2)

    service.showNotifications(clock)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(shownNotifications).hasSize(1)
    assertThat(shownNotifications.first().notificationSpec.id).isEqualTo(campaign2.notificationSpec.id)
  }

  @Test
  fun testShowNotifications_notificationFlagMissing() = runTest {
    val notificationFlagName = "test_notification"
    val notificationList = StudioPushNotificationList.newBuilder().addNotifications(notificationFlagName).build()
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/notification_flag_list", notificationList)

    service.showNotifications(clock)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(shownNotifications).isEmpty()
    verify(testLogger).debug("Unable to find server side flag for notification: $notificationFlagName")
  }

  @Test
  fun testShowNotifications_flagListMissing() = runTest {
    service.showNotifications(clock)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(shownNotifications).isEmpty()
    verify(testLogger).debug("Unable to extract the flag list.")
  }

  private fun registerNotification(notification: StudioPushNotification, flagName: String = "test_notification") {
    val notificationList = StudioPushNotificationList.newBuilder().addNotifications(flagName).build()
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/notification_flag_list", notificationList)
    serverFlagService.registerFlag("studio_flags/studiobot_push_notifications/$flagName", notification)
  }

  private val gotItTooltipMissingId: StudioPushNotification =
    StudioPushNotification.newBuilder().setGotItTooltipSpec(GotItTooltipSpec.getDefaultInstance()).build()

  private val gotItTooltipWithAllData: StudioPushNotification =
    StudioPushNotification.newBuilder()
      .setGotItTooltipSpec(
        GotItTooltipSpec.newBuilder()
          .setId("GOT_IT_TOOLTIP_WITH_ALL_DATA")
          .build()
      )
      .build()

  private val balloonNotificationWithAllData: StudioPushNotification =
    StudioPushNotification.newBuilder()
      .setNotificationSpec(
        NotificationSpec.newBuilder()
          .setId("BALLOON_NOTIFICATION_WITH_ALL_DATA")
          .setTitle("title")
          .setDescription("description")
          .build()
      )
      .build()

  private fun createNotificationWithPriority(id: String, priority: StudioPushNotification.Priority): StudioPushNotification {
    return balloonNotificationWithAllData.toBuilder()
      .setNotificationSpec(
        balloonNotificationWithAllData.notificationSpec.toBuilder().setId(id)
      )
      .setPriority(priority)
      .build()
  }
}
