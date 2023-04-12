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
import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel


class DevicesConnectionStepTest : LightPlatform4TestCase() {
  private val invokeStrategy = TestInvokeStrategy()
  /** A UsageTracker implementation that allows introspection of logged metrics in tests. */
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())
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
    // Studio Icons must be of type CachedImageIcon for image asset
    IconLoaderRule.enableIconLoading()
    super.setUp()

    model.selectedPhoneDevice.value = phoneDevice
    model.selectedWearDevice.value = wearDevice

    BatchInvoker.setOverrideStrategy(invokeStrategy)
    UsageTracker.setWriterForTest(usageTracker)
  }

  override fun tearDown() {
    try {
      BatchInvoker.clearOverrideStrategy()
      usageTracker.close()
      UsageTracker.cleanAfterTesting()
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
  }

  private fun shouldPromptToInstallCompanionApp(iDevice: IDevice) {
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    fakeUi.waitForHeader("Install Wear OS Companion Application")
    waitForCondition(fakeUi, 5) { getWearPairingTrackingEvents().isNotEmpty() }
    assertThat(getWearPairingTrackingEvents().last().studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_INSTALL_WEAR_OS_COMPANION)
  }

  @Test
  fun shouldPromptToInstallPixelCompanionApp_ifPixelCompanionAppIdSet() {
    val iDevice = createTestDevice(companionAppId = "com.google.android.apps.wear.companion")
    shouldPromptToInstallCompanionApp(iDevice)
  }

  @Test
  fun shouldPromptToInstallLegacyCompanionApp_ifCompanionAppIdNotSpecified() {
    val iDevice = createTestDevice(companionAppId = null)
    shouldPromptToInstallCompanionApp(iDevice)
  }

  @Test
  fun shouldWarnAboutUnknownCompanionApp() {
    val iDevice = createTestDevice(companionAppId = "some.unknown.companion.app")
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      fakeUi.findComponent<LinkLabel<Any>> { it.text == "Retry" } != null
    }
  }

  @Test
  fun stepShouldEnableGoForwardIfCompanionAppFound() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0") // Simulate Companion App
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, wizard) = createDeviceConnectionStepUi()

    assertThat(wizard.canGoForward().get()).isFalse()

    waitForCondition(fakeUi, 5) {
      invokeStrategy.updateAllSteps()
      wizard.canGoForward().get()
    }
  }

  @Test
  fun shouldShowSuccessIfAlreadyPaired() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0")
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      invokeStrategy.updateAllSteps()
      fakeUi.findComponent<JBLabel> { it.text == "Successful pairing" } != null
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

    waitForCondition(fakeUi, 15) {
      fakeUi.findComponent<JLabel> { it.text == "My Phone didn't start" } != null
    }

    fakeUi.layoutAndDispatchEvents()
    fakeUi.findComponent<JButton> { it.text == "Try again" }!!.apply {
      this.doClick()
    }

    assertThat(launchedCalled).isTrue()
    assertThat(wizardAction.restartCalled).isTrue()
  }

  @Test
  fun shouldShowErrorIfWatchGsmcoreIsOld() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0", 0) // Simulate Companion App
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      fakeUi.findComponent<JLabel> { it.text == "Restart pairing" } != null
    }
  }

  @Test
  fun shouldShowErrorIfAgpConnectionFails() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0") // Simulate Companion App
    whenever(iDevice.createForward(Mockito.anyInt(), Mockito.anyInt())).thenThrow(RuntimeException("Test"))
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      invokeStrategy.updateAllSteps()
      fakeUi.findComponent<JBLabel> { it.text == "Error occurred connecting devices" } != null
    }
  }

  @Test
  fun shouldShowFactoryResetIfPairingStatusDosntMatch() {
    val iDevice = createTestDevice(companionAppVersion = "versionName=1.0.0") { request ->
      when {
        request.contains("get-pairing-status") ->
          "Broadcasting: Intent { act=com.google.android.gms.wearable.EMULATOR flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Local:[LocalNodeId]\nPeer:[AnotherNode,false,false]\""
        else -> null
      }
    }
    phoneDevice.launch = { Futures.immediateFuture(iDevice) }
    wearDevice.launch = phoneDevice.launch

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      invokeStrategy.updateAllSteps()
      fakeUi.findComponent<JLabel> { it.text == "Factory reset Wear OS emulator" } != null
    }
  }

  @Test
  fun shouldShowFactoryResetIfCloudNodeIdDoesntMatchOnOldGmscore() {
    phoneDevice.launch = {
      Futures.immediateFuture(createTestDevice(gmscoreVersion = PairingFeature.GET_PAIRING_STATUS.minVersion - 1,
                                               companionAppVersion = "versionName=1.0.0") { request ->
        when {
          request.contains("cloud network id:") ->
            "cloud network id: aaa"
          else -> null
        }
      })
    }
    wearDevice.launch = {
      Futures.immediateFuture(createTestDevice(gmscoreVersion = PairingFeature.GET_PAIRING_STATUS.minVersion - 1,
                                               companionAppVersion = "versionName=1.0.0") { request ->
        when {
          request.contains("cloud network id:") ->
            "cloud network id: bbb"
          else -> null
        }
      })
    }

    val (fakeUi, _) = createDeviceConnectionStepUi()

    waitForCondition(fakeUi, 15) {
      invokeStrategy.updateAllSteps()
      fakeUi.findComponent<JLabel> { it.text == "Factory reset Wear OS emulator" } != null
    }
  }

  private fun createDeviceConnectionStepUi(wizardAction: WizardAction = WizardActionTest()): Pair<FakeUi, ModelWizard> {
    val deviceConnectionStep = DevicesConnectionStep(model, project, wizardAction)
    Disposer.register(testRootDisposable, deviceConnectionStep)

    val modelWizard = ModelWizard.Builder().addStep(deviceConnectionStep).build()
    Disposer.register(testRootDisposable, modelWizard)

    modelWizard.contentPanel.size = Dimension(600, 400)
    invokeStrategy.updateAllSteps()

    return Pair(FakeUi(modelWizard.contentPanel), modelWizard)
  }

  private fun waitForCondition(fakeUi: FakeUi, timeout: Long, condition: () -> Boolean) {
    try {
      waitForCondition(timeout, TimeUnit.SECONDS, condition)
    }
    catch (ex: Throwable) {
      fakeUi.dump()
      throw ex
    }
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitForHeader(text: String) = waitForCondition(this, 5) {
    findComponent<JBLabel> { it.name == "header" && it.text == text } != null
  }

  private fun getWearPairingTrackingEvents(): List<LoggedUsage> =
    usageTracker.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.WEAR_PAIRING}

  private fun createTestDevice(companionAppVersion: String = "",
                               gmscoreVersion: Int = Int.MAX_VALUE,
                               companionAppId: String? = null,
                               additionalReplies: (request: String) -> String? = { null }): IDevice {
    val iDevice = Mockito.mock(IDevice::class.java)
    whenever(
      iDevice.executeShellCommand(
        Mockito.anyString(),
        Mockito.any()
      )
    ).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver

      val reply = additionalReplies(request)?: when {
        request == "am force-stop com.google.android.gms" -> "OK"
        request.contains("grep 'local: '") -> "local: TestNodeId"
        // Note: get-pairing-status gets called on both phone and watch. Watch uses the Local part and phone uses the Peer part.
        request.contains("get-pairing-status") ->
          "Broadcasting: Intent { act=com.google.android.gms.wearable.EMULATOR flg=0x400000 (has extras) }\n" +
          "Broadcast completed: result=1, data=\"Local:[TestNodeId]\nPeer:[TestNodeId,true,true]\nPeer:[AnotherNode,false,false]\""
        request.contains("grep versionName") -> companionAppVersion
        request.contains("grep versionCode") -> "versionCode=$gmscoreVersion"
        request.contains("settings get secure") -> companionAppId.toString()
        else -> "Unknown executeShellCommand request $request"
      }

      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }

    whenever(iDevice.arePropertiesSet()).thenReturn(true)
    whenever(iDevice.getProperty("dev.bootcomplete")).thenReturn("1")
    whenever(iDevice.getSystemProperty("ro.oem.companion_package")).thenReturn(Futures.immediateFuture(""))

    return iDevice
  }
}

internal class WizardActionTest : WizardAction {
  var closeCalled = false
  var restartCalled = false

  override fun closeAndStartAvd(project: Project?) {
    closeCalled = true
  }

  override fun restart(project: Project?) {
    restartCalled = true
  }
}