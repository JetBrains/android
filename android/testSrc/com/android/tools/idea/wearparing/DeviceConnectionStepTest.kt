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

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wearparing.ConnectionState.ONLINE
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.JLabel


class DeviceConnectionStepTest : LightPlatform4TestCase() {

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
  fun shouldShowGenericErrorIfADeviceWasSelected() {
    model.selectedPhoneDevice.setNullableValue(null)
    val (fakeUi, _) = createDeviceConnectionStepUi()

    fakeUi.waitForHeader("Found a problem")
  }

  @Test
  fun shouldLaunchDevices() {
    var launched = false
    phoneDevice.launch = {
      launched = true
      Futures.immediateFuture(createTestDevice(companionAppVersion = ""))
    }
    wearDevice.launch = phoneDevice.launch

    assertThat(model.removePairingOnCancel.get()).isFalse()

    val (fakeUi, _) = createDeviceConnectionStepUi()

    fakeUi.waitForHeader("Install Wear OS Companion Application")
    assertThat(launched).isTrue()
    assertThat(model.removePairingOnCancel.get()).isTrue()

    val (pairedPhoneDevice, pairedWearDevice) = WearPairingManager.getPairedDevices()
    assertThat(pairedPhoneDevice?.deviceID).isEqualTo(phoneDevice.deviceID)
    assertThat(pairedWearDevice?.deviceID).isEqualTo(wearDevice.deviceID)
  }

  @Test
  fun stepShouldAskToInstallWearOSCompanionApp() {
    val iDevice = createTestDevice(companionAppVersion = "") // Simulate no Companion App
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    fakeUi.waitForHeader("Install Wear OS Companion Application")
  }

  @Test
  fun stepShouldEnableGoForwardIfCompanionAppFound() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0") // Simulate Companion App
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, wizard) = createDeviceConnectionStepUi()

    assertThat(wizard.canGoForward().get()).isFalse()

    fakeUi.waitForHeader("Starting devices")
    waitForCondition(5, TimeUnit.SECONDS) {
      invokeStrategy.updateAllSteps()
      wizard.canGoForward().get()
    }
  }

  @Test
  fun shouldShowRestartPairingIfConnectionIsDrop() {
    var launchedCalled = false

    phoneDevice.launch = {
      launchedCalled = true
      Futures.immediateFailedFuture(RuntimeException("Test launching exception"))
    }
    wearDevice.launch = phoneDevice.launch

    val wizardAction = WizardActionTest()
    val (fakeUi, _) = createDeviceConnectionStepUi(wizardAction)

    waitForCondition(15, TimeUnit.SECONDS) {
      fakeUi.findComponent<JLabel> { it.text == "Restart pairing" } != null
    }

    fakeUi.layoutAndDispatchEvents()
    fakeUi.findComponent<LinkLabel<Any>> { it.text == "Restart pairing" }!!.apply {
      this.doClick()
    }

    assertThat(launchedCalled).isTrue()
    assertThat(wizardAction.restartCalled).isTrue()
  }

  private fun createDeviceConnectionStepUi(wizardAction: WizardAction = WizardActionTest()): Pair<FakeUi, ModelWizard> {
    val deviceConnectionStep = DevicesConnectionStep(model, project, wizardAction)
    val modelWizard = ModelWizard.Builder().addStep(deviceConnectionStep).build()
    Disposer.register(testRootDisposable, modelWizard)
    invokeStrategy.updateAllSteps()

    modelWizard.contentPanel.size = Dimension(600, 400)

    return Pair(FakeUi(modelWizard.contentPanel), modelWizard)
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitForHeader(text: String) = waitForCondition(5, TimeUnit.SECONDS) {
    findComponent<JBLabel> { it.name == "header" && it.text == text } != null
  }

  private fun createTestDevice(companionAppVersion: String): IDevice {
    val iDevice = Mockito.mock(IDevice::class.java)
    Mockito.`when`(
      iDevice.executeShellCommand(
        Mockito.anyString(),
        Mockito.any()
      )
    ).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver

      val reply = when {
        request == "am force-stop com.google.android.gms" -> "OK"
        request.contains("grep 'local: '") -> "local: TestNodeId"
        request.contains("grep versionName") -> companionAppVersion
        else -> "Unknown executeShellCommand request $request"
      }

      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }

    Mockito.`when`(iDevice.arePropertiesSet()).thenReturn(true)
    Mockito.`when`(iDevice.getProperty("dev.bootcomplete")).thenReturn("1")

    return iDevice
  }
}

internal class WizardActionTest : WizardAction {
  var closeCalled = false
  var restartCalled = false

  override fun closeAndStartAvd(project: Project) {
    closeCalled = true
  }

  override fun restart(project: Project) {
    restartCalled = true
  }
}