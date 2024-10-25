/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FakeActionPopupMenu
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@RunsInEdt
class WearHealthServicesPanelControllerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  @get:Rule val edtRule = EdtRule()
  @get:Rule val fakePopupRule = JBPopupRule()

  private val notifications
    get() =
      NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, projectRule.project)
        .toList()

  private lateinit var fakePopup: FakeActionPopupMenu
  private lateinit var uiScope: CoroutineScope
  private lateinit var workerScope: CoroutineScope
  private lateinit var deviceManager: FakeDeviceManager
  private lateinit var stateManager: WearHealthServicesStateManagerImpl
  private lateinit var controller: WearHealthServicesPanelController

  private val fakeUi: FakeUi
    get() =
      FakeUi(
        fakePopupRule.fakePopupFactory
          .getBalloon(fakePopupRule.fakePopupFactory.balloonCount - 1)
          .component
      )

  @Before
  fun setup() {
    uiScope = AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.uiThread)
    workerScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.workerThread)
    deviceManager = FakeDeviceManager()

    stateManager =
      WearHealthServicesStateManagerImpl(deviceManager = deviceManager, workerScope = workerScope)
        .also { Disposer.register(projectRule.testRootDisposable, it) }
        .also { it.serialNumber = "some serial number" }
    controller =
      WearHealthServicesPanelController(
        stateManager = stateManager,
        uiScope = uiScope,
        workerScope = workerScope,
      )

    val actionManager = spy(ActionManager.getInstance() as ActionManagerEx)
    doAnswer { invocation ->
        fakePopup = FakeActionPopupMenu(invocation.getArgument(1))
        fakePopup
      }
      .whenever(actionManager)
      .createActionPopupMenu(anyString(), any())
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, actionManager, projectRule.testRootDisposable)
  }

  @Test
  fun `test balloon shows`() {
    showWhsPopup()

    assertThat(fakePopupRule.fakePopupFactory.balloonCount).isEqualTo(1)
  }

  @Test
  fun `test user is notified of successful apply when panel is showing`(): Unit = runBlocking {
    showWhsPopup()

    fakeUi.clickOnApplyButton()
    // show popup again as clicking on the apply button closes it
    showWhsPopup()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.apply.capabilities.success") &&
          it.type == NotificationType.INFORMATION
      }
    }
  }

  @Test
  fun `test user is notified of failed apply changes when panel is showing`(): Unit = runBlocking {
    deviceManager.failState = true

    showWhsPopup()
    fakeUi.clickOnApplyButton()

    // show popup again as clicking on the apply button closes it
    showWhsPopup()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.apply.capabilities.failure") &&
          it.type == NotificationType.ERROR
      }
    }
  }

  @Test
  fun `test user is notified of successful apply changes when panel is not showing`(): Unit =
    runBlocking {
      showWhsPopup()

      fakeUi.clickOnApplyButton()

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.capabilities.success") &&
            it.type == NotificationType.INFORMATION
        }
      }
    }

  @Test
  fun `test user is notified of failed apply changes when panel is not showing`(): Unit =
    runBlocking {
      deviceManager.failState = true

      showWhsPopup()

      fakeUi.clickOnApplyButton()

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.capabilities.failure") &&
            it.type == NotificationType.ERROR
        }
      }
    }

  @Test
  fun `test apply notifies about sensor value changes when there is an ongoing exercise`(): Unit =
    runBlocking {
      deviceManager.activeExercise = true
      stateManager.ongoingExercise.waitForValue(true)

      showWhsPopup()

      deviceManager.failState = false
      fakeUi.clickOnApplyButton()

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.sensor.values.success") &&
            it.type == NotificationType.INFORMATION
        }
      }

      showWhsPopup()
      deviceManager.failState = true
      fakeUi.clickOnApplyButton()

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.panel.apply.sensor.values.failure") &&
            it.type == NotificationType.ERROR
        }
      }
    }

  @Test
  fun `test successful reset shows in information label when panel is showing`(): Unit =
    runBlocking {
      showWhsPopup()

      val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
      resetButton.doClick()

      fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.reset.success") }
    }

  @Test
  fun `test failed reset shows in information label when panel is showing`(): Unit = runBlocking {
    deviceManager.failState = true

    showWhsPopup()

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
    resetButton.doClick()

    fakeUi.waitForDescendant<JLabel> { it.text == message("wear.whs.panel.reset.failure") }
  }

  @Test
  fun `test user is notified of successful reset when panel is not showing`(): Unit = runBlocking {
    showWhsPopup()

    fakePopupRule.fakePopupFactory.getBalloon(0).hide()

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }
    resetButton.doClick()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.reset.success") &&
          it.type == NotificationType.INFORMATION
      }
    }
  }

  @Test
  fun `test user is notified of failed reset when panel is not showing`(): Unit = runBlocking {
    showWhsPopup()

    deviceManager.failState = true

    val resetButton = fakeUi.waitForDescendant<JButton> { it.text == "Reset" }

    // hide WHS panel
    fakePopupRule.fakePopupFactory.getBalloon(0).hide()

    resetButton.doClick()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.panel.reset.failure") && it.type == NotificationType.ERROR
      }
    }
  }

  @Test
  fun `test user is notified of successful trigger event when panel is showing`(): Unit =
    runBlocking {
      showWhsPopup()

      fakeUi.clickOnTriggerEvent({ fakePopup })

      // show popup again as clicking on the trigger event button closes it
      showWhsPopup()

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.event.trigger.success") &&
            it.type == NotificationType.INFORMATION
        }
      }
    }

  @Test
  fun `test user is notified of failed trigger event when panel is showing`(): Unit = runBlocking {
    deviceManager.failState = true

    showWhsPopup()

    fakeUi.clickOnTriggerEvent({ fakePopup })
    // show popup again as clicking on the trigger event button closes it
    showWhsPopup()

    waitForCondition(2, TimeUnit.SECONDS) {
      notifications.any {
        it.content == message("wear.whs.event.trigger.failure") && it.type == NotificationType.ERROR
      }
    }
  }

  @Test
  fun `test user is notified of successful trigger event when panel is not showing`(): Unit =
    runBlocking {
      showWhsPopup()

      fakeUi.clickOnTriggerEvent({ fakePopup })

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.event.trigger.success") &&
            it.type == NotificationType.INFORMATION
        }
      }
    }

  @Test
  fun `test user is notified of failed trigger event when panel is not showing`(): Unit =
    runBlocking {
      showWhsPopup()

      deviceManager.failState = true

      fakeUi.clickOnTriggerEvent({ fakePopup })

      waitForCondition(2, TimeUnit.SECONDS) {
        notifications.any {
          it.content == message("wear.whs.event.trigger.failure") &&
            it.type == NotificationType.ERROR
        }
      }
    }

  private fun showWhsPopup() {
    controller.showWearHealthServicesToolPopup(
      projectRule.testRootDisposable,
      RelativePoint(mock(), Point(0, 0)),
    )
  }
}
