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

import com.android.tools.idea.serverflags.protos.StudioPushNotification
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Actions
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Actions.BrowseAction
import com.android.tools.idea.serverflags.protos.StudioPushNotification.GotItTooltipSpec
import com.android.tools.idea.serverflags.protos.StudioPushNotification.GotItTooltipSpec.PlacementDetails
import com.android.tools.idea.serverflags.protos.StudioPushNotification.GotItTooltipSpec.PlacementDetails.Position
import com.android.tools.idea.serverflags.protos.StudioPushNotification.NotificationSpec
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.DateCondition
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.FeatureUsage
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.FeatureUsage.Feature
import com.android.tools.idea.serverflags.protos.StudioPushNotification.Requirement.ModelProviderType
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Timestamp
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(JUnit4::class)
class DefaultServerPushNotificationBridgeTest : LightIdeaTestCase() {

  private lateinit var bridge: DefaultServerPushNotificationBridge
  private val notifications = CopyOnWriteArrayList<Notification>()
  private val mockListener: ServerPushNotificationListener = mock()

  override fun setUp() {
    super.setUp()
    bridge = DefaultServerPushNotificationBridge(project)

    project.messageBus
      .connect(testRootDisposable)
      .subscribe(
        Notifications.TOPIC,
        object : Notifications {
          override fun notify(notification: Notification) {
            notifications.add(notification)
          }
        },
      )
  }

  @Test
  fun testShowNotification_rendersBalloonWithBrowseAction() {
    val browseAction =
      Actions.newBuilder().setTitle("Browse").setBrowseAction(BrowseAction.newBuilder().setUrl("https://google.com")).build()
    val notification =
      StudioPushNotification.newBuilder()
        .setNotificationSpec(
          NotificationSpec.newBuilder()
            .setId("BALLOON_WITH_BROWSE")
            .setTitle("Title")
            .setDescription("Description")
            .addActions(browseAction)
            .build()
        )
        .build()

    val handled = bridge.showNotification(notification, mockListener)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(handled).isTrue()
    assertThat(notifications).hasSize(1)
    val balloon = notifications.first()
    assertThat(balloon.title).isEqualTo("Title")
    assertThat(balloon.actions).hasSize(1)
    assertThat(balloon.actions.first().templateText).isEqualTo("Browse")

    (balloon.actions.first() as NotificationAction).actionPerformed(mock<AnActionEvent>(), balloon)

    verify(mockListener).onNotificationShown("BALLOON_WITH_BROWSE")
    verify(mockListener).onNotificationActionClicked("BALLOON_WITH_BROWSE", "Browse")
  }

  @Test
  fun testShowNotification_rendersBalloonWithUiScreenId() {
    val uiAction =
      Actions.newBuilder()
        .setTitle("New Project")
        .setUiScreenId("NewProject")
        .putDataContextParams("NPW_INITIAL_TARGET_KEY", "Wear")
        .build()
    val notification =
      StudioPushNotification.newBuilder()
        .setNotificationSpec(
          NotificationSpec.newBuilder()
            .setId("BALLOON_WITH_UI_ACTION")
            .setTitle("Title")
            .setDescription("Description")
            .addActions(uiAction)
            .build()
        )
        .build()

    val handled = bridge.showNotification(notification, mockListener)
    UIUtil.dispatchAllInvocationEvents()

    assertThat(handled).isTrue()
    assertThat(notifications).hasSize(1)
    val balloon = notifications.first()
    assertThat(balloon.actions.first().templateText).isEqualTo("New Project")

    (balloon.actions.first() as NotificationAction).actionPerformed(mock<AnActionEvent>(), balloon)

    verify(mockListener).onNotificationShown("BALLOON_WITH_UI_ACTION")
    verify(mockListener).onNotificationActionClicked("BALLOON_WITH_UI_ACTION", "New Project")
  }

  @Test
  fun testShowNotification_gotItTooltipSpec_returnsFalse() {
    val notification =
      StudioPushNotification.newBuilder()
        .setGotItTooltipSpec(
          GotItTooltipSpec.newBuilder()
            .setId("GOT_IT_TOOLTIP_SPEC")
            .setPlacementDetails(PlacementDetails.newBuilder().setPosition(Position.MODEL_PICKER).build())
            .build()
        )
        .build()

    val handled = bridge.showNotification(notification, mockListener)
    assertThat(handled).isFalse()
  }

  @Test
  fun testCheckRequirement_returnsNullForUnhandled() {
    val unhandledRequirement = Requirement.newBuilder().setModelProviderType(ModelProviderType.FREE_TIER).build()
    assertThat(bridge.checkRequirement(unhandledRequirement)).isNull()
  }

  @Test
  fun testCheckRequirement_daysSinceLastUse() {
    val requirement =
      Requirement.newBuilder().setFeatureUsage(FeatureUsage.newBuilder().setFeature(Feature.AGENT_WINDOW).setDaysSinceLastUse(5)).build()

    // Feature was never used -> satisfied
    assertThat(bridge.checkRequirement(requirement)).isTrue()

    // Feature was used recently -> unfulfilled
    ServerNotificationStore.getInstance().recordFeatureUsage(Feature.AGENT_WINDOW_VALUE)
    assertThat(bridge.checkRequirement(requirement)).isFalse()
  }

  @Test
  fun testCheckRequirement_dateCondition() {
    val nowSeconds = Clock.System.now().epochSeconds

    val validRequirement =
      Requirement.newBuilder()
        .setDateCondition(
          DateCondition.newBuilder()
            .setShownAfter(Timestamp.newBuilder().setSeconds(nowSeconds - 100))
            .setShownBefore(Timestamp.newBuilder().setSeconds(nowSeconds + 100))
        )
        .build()
    assertThat(bridge.checkRequirement(validRequirement)).isTrue()

    val expiredRequirement =
      Requirement.newBuilder()
        .setDateCondition(DateCondition.newBuilder().setShownBefore(Timestamp.newBuilder().setSeconds(nowSeconds - 100)))
        .build()
    assertThat(bridge.checkRequirement(expiredRequirement)).isFalse()
  }
}
