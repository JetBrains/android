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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.compose.InformationPopup
import com.android.tools.adtui.compose.IssueNotificationAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import com.intellij.util.Alarm
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class IssueNotificationActionTest {

  @get:Rule
  val projectRule = ProjectRule()

  /**
   * Function to create the test [IssueNotificationAction].
   */
  private fun createIssueNotificationAction(
    popup: InformationPopup,
    alarm: Alarm
  ) = IssueNotificationAction(
    createStatusInfo = { _, _ -> null },
    createInformationPopup = { _, _ -> popup },
    popupAlarm = alarm
  ).also {
    // Adding a fake action event with the action event that contains our fake [DataContext]
    it.actionEventCreator = { _, _ -> projectRule.createFakeActionEvent(it) }
  }

  @Test
  fun testTheAlarmShowsPopupOnMouseEnter() {
    // Given the popup not visible
    var showPopup = false
    val fakePopup: InformationPopup = createFakePopup(
      onShowPopup = {
        showPopup = true
      }
    )

    var actualDelayMillis = 0
    val fakeAlarm = createFakeAlarm(onAddRequest = { delayMillis ->
      actualDelayMillis = delayMillis
    })

    val issueNotificationAction = createIssueNotificationAction(fakePopup, fakeAlarm)

    // When the mouse enters the action button
    issueNotificationAction.mouseListener.mouseEntered(createFakeMouseEvent())

    // The request is dispatched after a certain delay
    assertTrue { actualDelayMillis == Registry.intValue("ide.tooltip.initialReshowDelay") }

    // The onShowPopup callback get called
    assertTrue { showPopup }
  }

  @Test
  fun testAlarmIsCanceledOnMouseEnterPopup() {
    // Given the popup
    val fakePopup = createFakePopup()

    var cancelAllRequestsCounter = 0
    val fakeAlarm = createFakeAlarm(onCancelAllRequest = { cancelAllRequestsCounter++ })

    val issueNotificationAction = createIssueNotificationAction(fakePopup, fakeAlarm)
    val fakeMouseEvent = createFakeMouseEvent()

    // When the mouse enters the action button
    issueNotificationAction.mouseListener.mouseEntered(fakeMouseEvent)

    // And the mouse exits the action button
    issueNotificationAction.mouseListener.mouseExited(fakeMouseEvent)

    // Should call `Alarm#cancelAllRequests`
    fakePopup.onMouseEnteredCallback()

    // Alarm cancel the request four times
    assertTrue { cancelAllRequestsCounter == 4 }
  }
}

