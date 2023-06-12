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
package com.android.build.attribution

import com.android.testutils.MockitoKt
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.diagnostic.WindowsDefenderCheckerWrapper
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class WindowsDefenderCheckServiceTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  var notificationCounter = 0

  @Before
  fun setup() {
    projectRule.project.messageBus.connect(projectRule.testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "WindowsDefender") {
          notificationCounter++
        }
      }
    })
  }

  @Test
  fun testServiceCreatedWithoutCheckOnProjectOpen() {
    val service = WindowsDefenderCheckService(projectRule.project) {
      Truth.assert_().fail("Checker should not be requested in this test case.")
      Mockito.mock(WindowsDefenderCheckerWrapper::class.java)
    }
    Truth.assertThat(service.warningData).isEqualTo(WindowsDefenderCheckService.NO_WARNING)
  }

  @Test
  fun testServiceCreatedAndStatusCheckRun() {
    testServiceCreatedAndStatusCheckRun(checkIgnored = false, protectionStatus = null, expectedWarningShown = false)
    testServiceCreatedAndStatusCheckRun(checkIgnored = false, protectionStatus = false, expectedWarningShown = false)
    testServiceCreatedAndStatusCheckRun(checkIgnored = false, protectionStatus = true, expectedWarningShown = true)
    testServiceCreatedAndStatusCheckRun(checkIgnored = true, protectionStatus = null, expectedWarningShown = false)
    testServiceCreatedAndStatusCheckRun(checkIgnored = true, protectionStatus = false, expectedWarningShown = false)
    testServiceCreatedAndStatusCheckRun(checkIgnored = true, protectionStatus = true, expectedWarningShown = false)
  }

  private fun testServiceCreatedAndStatusCheckRun(checkIgnored: Boolean, protectionStatus: Boolean?, expectedWarningShown: Boolean) {
    notificationCounter = 0
    val checkerWrapperMock = Mockito.mock(WindowsDefenderCheckerWrapper::class.java)
    Mockito.`when`(checkerWrapperMock.isStatusCheckIgnored(MockitoKt.any())).thenReturn(checkIgnored)
    Mockito.`when`(checkerWrapperMock.isRealTimeProtectionEnabled).thenReturn(protectionStatus)

    val service = WindowsDefenderCheckService(projectRule.project) { checkerWrapperMock }
    service.checkRealTimeProtectionStatus()

    Truth.assertThat(service.warningData.shouldShowWarning).isEqualTo(expectedWarningShown)
    Truth.assertThat(notificationCounter).isEqualTo(if (expectedWarningShown) 1 else 0)
  }

  @Test
  fun testResetActionExist() {
    // We reference it in instruction to reset, so check it indeed still in place.
    Truth.assertThat(ActionManager.getInstance().getAction("ResetWindowsDefenderNotification")).isNotNull()
  }
}