/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearparing

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wearparing.ConnectionState.ONLINE
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import org.junit.Test
import java.awt.Dimension
import java.util.concurrent.TimeUnit


class NewConnectionAlertStepTest : LightPlatform4TestCase() {

  private val invokeStrategy = TestInvokeStrategy()
  private val model = WearDevicePairingModel()
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ONLINE, isPaired = false
  )

  override fun setUp() {
    super.setUp()

    phoneDevice.launch = { throw RuntimeException("Can't launch on tests") }
    wearDevice.launch = phoneDevice.launch
    model.selectedPhoneDevice.value = phoneDevice
    model.selectedWearDevice.value = wearDevice

    BatchInvoker.setOverrideStrategy(invokeStrategy)
  }

  override fun tearDown() {
    try {
      BatchInvoker.clearOverrideStrategy()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun shouldShowIfMoreThanOneWearIsRunning() {
    model.wearList.set(listOf(
      wearDevice.copy(deviceID = "id3", displayName = "My Watch"),
    ))

    val fakeUi = createNewConnectionAlertStepUi()
    fakeUi.waitForText("Shutting down other Wear OS emulators")
  }

  @Test
  fun shouldShowIfPreviousPairingIsActive() {
    val previousPairedWear = wearDevice.copy(deviceID = "id3", isPaired = true).apply {
      launch = wearDevice.launch
    }
    WearPairingManager.setPairedDevices(phoneDevice, previousPairedWear)

    val fakeUi = createNewConnectionAlertStepUi()
    fakeUi.waitForText("Disconnecting existing devices")
  }

  private fun createNewConnectionAlertStepUi(): FakeUi {
    val newConnectionAlertStep = NewConnectionAlertStep(model, project)
    val modelWizard = ModelWizard.Builder().addStep(newConnectionAlertStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)

    return FakeUi(modelWizard.contentPanel)
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitForText(text: String) = waitForCondition(5, TimeUnit.SECONDS) {
    findComponent<JBLabel> { println(">>> ${it.text}"); it.text == text } != null
  }
}