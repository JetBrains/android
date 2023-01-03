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

import com.android.ddmlib.AvdData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle.Companion.message
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.swing.JButton

class EndToEndIntegrationTest : LightPlatform4TestCase() {
  private val invokeStrategy = TestInvokeStrategy()
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun setUp() {
    // Studio Icons must be of type CachedImageIcon for image asset
    IconLoaderRule.enableIconLoading()
    super.setUp()
    BatchInvoker.setOverrideStrategy(invokeStrategy)
    UsageTracker.setWriterForTest(usageTracker)
    enableHeadlessDialogs(testRootDisposable)
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
  fun allInstalledQuickPathToSuccess() {
    val phoneIDevice = Mockito.mock(IDevice::class.java).apply {
      whenever(arePropertiesSet()).thenReturn(true)
      whenever(isOnline).thenReturn(true)
      whenever(name).thenReturn("MyPhone")
      whenever(serialNumber).thenReturn("serialNumber")
      whenever(state).thenReturn(IDevice.DeviceState.ONLINE)
      whenever(version).thenReturn(AndroidVersion(28, null))
      whenever(getProperty("dev.bootcomplete")).thenReturn("1")

      addExecuteShellCommandReply { request ->
        when {
          request == "cat /proc/uptime" -> "500"
          request.contains("grep versionName") -> "versionName=1.0.0"
          request.contains("grep versionCode") -> "versionCode=${PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING.minVersion}"
          request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
          else -> "Unknown executeShellCommand request $request"
        }
      }
    }

    val wearPropertiesMap = mapOf(AvdManager.AVD_INI_TAG_ID to "android-wear", AvdManager.AVD_INI_ANDROID_API to "28")
    val avdWearInfo = AvdInfo("My Wear", Paths.get("ini"), Paths.get("folder"), Mockito.mock(ISystemImage::class.java), wearPropertiesMap)

    val wearIDevice = Mockito.mock(IDevice::class.java).apply {
      whenever(arePropertiesSet()).thenReturn(true)
      whenever(isOnline).thenReturn(true)
      whenever(isEmulator).thenReturn(true)
      whenever(name).thenReturn(avdWearInfo.name)
      whenever(serialNumber).thenReturn("serialNumber")
      whenever(state).thenReturn(IDevice.DeviceState.ONLINE)
      whenever(version).thenReturn(AndroidVersion(28, null))
      whenever(avdData).thenReturn(Futures.immediateFuture(AvdData(avdWearInfo.name, avdWearInfo.dataFolderPath.toString())))
      whenever(getProperty("dev.bootcomplete")).thenReturn("1")
      whenever(getSystemProperty("ro.oem.companion_package")).thenReturn(Futures.immediateFuture(""))
      addExecuteShellCommandReply { request ->
        when {
          request == "cat /proc/uptime" -> "500"
          request == "am force-stop com.google.android.gms" -> "OK"
          request.contains("grep versionCode") -> "versionCode=${PairingFeature.REVERSE_PORT_FORWARD.minVersion}"
          request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
          request.contains("settings get secure") -> "null"
          else -> "Unknown executeShellCommand request $request"
        }
      }
    }

    WearPairingManager.getInstance().setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    assertThat(WearPairingManager.getInstance().getPairsForDevice(wearIDevice.name)).isEmpty()

    createModalDialogAndInteractWithIt({ WearDevicePairingWizard().show(null, null) }) {
      FakeUi(it.contentPane).apply {
        waitLabelText(message("wear.assistant.device.list.title"))
        clickButton("Next")
        waitLabelText(message("wear.assistant.device.connection.pairing.success.title"))
        clickButton("Finish")
      }
    }

    waitForCondition(5, TimeUnit.SECONDS) { getWearPairingTrackingEvents().size >= 2 }
    val usages = getWearPairingTrackingEvents()
    assertThat(usages[0].studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_ASSISTANT_FULL_SELECTION)
    assertThat(usages[1].studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)
    val phoneWearPair = WearPairingManager.getInstance().getPairsForDevice(avdWearInfo.id)
    assertThat(phoneWearPair).isNotEmpty()
    assertThat(phoneWearPair[0].pairingStatus).isEqualTo(WearPairingManager.PairingState.CONNECTED)
    assertThat(phoneWearPair[0].getPeerDevice(avdWearInfo.id).displayName).isEqualTo(phoneIDevice.name)
  }

  private fun FakeUi.clickButton(text: String) {
    waitForCondition(5, TimeUnit.SECONDS) {
      invokeStrategy.updateAllSteps()
      layoutAndDispatchEvents()
      findComponent<JButton> { text == it.text && it.isEnabled }?.apply { clickOn(this) } != null
    }
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitLabelText(text: String) = waitForCondition(5, TimeUnit.SECONDS) {
    invokeStrategy.updateAllSteps()
    layoutAndDispatchEvents()
    findComponent<JBLabel> { it.text == text } != null
  }

  private fun getWearPairingTrackingEvents(): List<LoggedUsage> =
    usageTracker.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.WEAR_PAIRING}

  private fun IDevice.addExecuteShellCommandReply(requestHandler: (request: String) -> String) {
    whenever(executeShellCommand(Mockito.anyString(), Mockito.any())).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver
      val reply = requestHandler(request)

      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }
  }
}