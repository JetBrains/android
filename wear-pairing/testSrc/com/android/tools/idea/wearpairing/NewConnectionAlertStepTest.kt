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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.util.concurrent.TimeUnit


class NewConnectionAlertStepTest : LightPlatform4TestCase() {

  private val invokeStrategy = TestInvokeStrategy()
  private val model = WearDevicePairingModel()
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ConnectionState.ONLINE
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ConnectionState.ONLINE
  )

  override fun setUp() {
    super.setUp()

    model.selectedPhoneDevice.value = phoneDevice
    model.selectedWearDevice.value = wearDevice

    BatchInvoker.setOverrideStrategy(invokeStrategy)
    WearPairingManager.getInstance().setDataProviders({ emptyList() }, { emptyList() })
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
  fun shouldShowIfWearMayNeedFactoryReset() {
    val previousPairedPhone = phoneDevice.copy(deviceID = "id4")
    val iDevice = Mockito.mock(IDevice::class.java)
    runBlocking { WearPairingManager.getInstance().createPairedDeviceBridge(previousPairedPhone, iDevice, wearDevice, iDevice, connect = false) }

    val fakeUi = createNewConnectionAlertStepUi()
    fakeUi.waitForText("Wear OS Factory Reset")
  }

  private fun createNewConnectionAlertStepUi(): FakeUi {
    val newConnectionAlertStep = NewConnectionAlertStep(model)
    val modelWizard = ModelWizard.Builder().addStep(newConnectionAlertStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)

    return FakeUi(modelWizard.contentPanel)
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitForText(text: String) = waitForCondition(5, TimeUnit.SECONDS) {
    findComponent<JBLabel> { it.text == text } != null
  }
}