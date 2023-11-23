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

import com.android.build.diagnostic.WindowsDefenderCheckService
import com.android.testutils.MockitoKt
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.diagnostic.WindowsDefenderChecker
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.LoggedErrorProcessor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.EnumSet

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
      Mockito.mock(WindowsDefenderChecker::class.java)
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
    val checkerMock = Mockito.mock(WindowsDefenderChecker::class.java)
    Mockito.`when`(checkerMock.isStatusCheckIgnored(MockitoKt.any())).thenReturn(checkIgnored)
    Mockito.`when`(checkerMock.isRealTimeProtectionEnabled).thenReturn(protectionStatus)

    val service = WindowsDefenderCheckService(projectRule.project) { checkerMock }
    service.checkRealTimeProtectionStatus()

    Truth.assertThat(service.warningData.shouldShowWarning).isEqualTo(expectedWarningShown)
    Truth.assertThat(notificationCounter).isEqualTo(if (expectedWarningShown) 1 else 0)
  }

  private class MyMockTestException : RuntimeException()

  @Test
  fun testStatusCheckRunFailure() {
    val checkerMock = Mockito.mock(WindowsDefenderChecker::class.java)
    Mockito.`when`(checkerMock.isStatusCheckIgnored(MockitoKt.any())).thenReturn(false)
    Mockito.`when`(checkerMock.isRealTimeProtectionEnabled).thenThrow(MyMockTestException())

    val service = WindowsDefenderCheckService(projectRule.project) { checkerMock }
    // Expect exception to be caught and logged.
    var exceptionWasLogged = false
    LoggedErrorProcessor.executeWith<MyMockTestException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        if (t is MyMockTestException) {
          exceptionWasLogged = true
          return EnumSet.noneOf(Action::class.java)
        }
        return super.processError(category, message, details, t)
      }
    }) {
      service.checkRealTimeProtectionStatus()
    }

    Truth.assertThat(service.warningData.shouldShowWarning).isEqualTo(false)
    Truth.assertThat(exceptionWasLogged).isEqualTo(true)
  }

  @Test
  fun testResetActionExist() {
    // We reference it in instruction to reset, so check it indeed still in place.
    Truth.assertThat(ActionManager.getInstance().getAction("ResetWindowsDefenderNotification")).isNotNull()
  }
}