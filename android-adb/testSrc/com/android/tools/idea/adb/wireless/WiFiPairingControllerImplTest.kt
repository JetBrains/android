/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.ddmlib.IDevice
import com.android.ddmlib.TimeoutRemainder
import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.ThreadingCheckRule
import com.google.common.truth.Truth
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.LineSeparator
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

class WiFiPairingControllerImplTest : LightPlatform4TestCase() {
  /** Ensures feature flag is reset after test */
  @get:Rule
  val flagRule = FlagRule(StudioFlags.ADB_WIRELESS_PAIRING_ENABLED)

  @get:Rule
  val portableUiFontRule = PortableUiFontRule()

  @get:Rule
  val threadingCheckRule = ThreadingCheckRule()

  @get:Rule
  val iconLoaderRule = IconLoaderRule()

  private val timeProvider: MockNanoTimeProvider by lazy { MockNanoTimeProvider() }

  private val randomProvider by lazy { MockRandomProvider() }

  private val adbService = mockOrActual<AdbServiceWrapper> {
    AdbServiceWrapperImpl(project, timeProvider)
  }

  private val devicePairingService: WiFiPairingService by lazy {
    WiFiPairingServiceImpl(randomProvider, adbService.instance)
  }

  private val notificationService: MockWiFiPairingNotificationService by lazy {
    MockWiFiPairingNotificationService(project)
  }

  private val model: MockWiFiPairingModel by lazy { MockWiFiPairingModel() }

  private val view: MockDevicePairingView by lazy {
    MockDevicePairingView(project, notificationService, model)
  }

  private val controller: WiFiPairingControllerImpl by lazy {
    WiFiPairingControllerImpl(project, testRootDisposable, devicePairingService, notificationService, view) {
      createPairingCodePairingController(it)
    }
  }

  private val testTimeUnit = TimeUnit.SECONDS
  private lateinit var testTimeout: TimeoutRemainder

  private var lastPairingCodeView: MockPairingCodePairingView? = null
  private var lastPairingCodeController: PairingCodePairingController? = null

    override fun setUp() {
      super.setUp()
      testTimeout = TimeoutRemainder(30, testTimeUnit)
      enableHeadlessDialogs(testRootDisposable)
    }

  @Suppress("SameParameterValue")
  private fun createPairingCodePairingController(mdnsService: MdnsService): PairingCodePairingController {
    val model = PairingCodePairingModel(mdnsService)
    val view = MockPairingCodePairingView(project, notificationService, model).also {
      lastPairingCodeView = it
    }
    return PairingCodePairingController(project.coroutineScope, devicePairingService, view).also {
      lastPairingCodeController = it
    }
  }

  @Test
  fun viewShouldShowErrorIfAdbPathIsNotSet() {
    // Prepare

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      // Assert
      pumpAndWait(view.showDialogTracker.consume())
      pumpAndWait(view.startMdnsCheckTracker.consume())
      pumpAndWait(view.showMdnsCheckErrorTracker.consume())
    }
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckIsNotSupported() {
    // Prepare
    adbService.useMock = true
    runBlocking {
      whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
        .thenReturn(AdbCommandResult(1, listOf(), listOf("unknown command")))

      // Act
      createModalDialogAndInteractWithIt({ controller.showDialog() }) {
        // Assert
        pumpAndWait(view.showDialogTracker.consume())
        pumpAndWait(view.startMdnsCheckTracker.consume())
        pumpAndWait(view.showMdnsNotSupportedByAdbErrorTracker.consume())
      }
    }
  }

  @Test
  fun viewShouldShowSdkManagerLinkIfOutOfDate() {
    createModalDialogAndInteractWithIt({ view.showDialog() }) {
      view.showMdnsNotSupportedByAdbError();

      val fakeUi = FakeUi(it.rootPane)
      val pane = fakeUi.getComponent<JEditorPane> {comp -> comp.name == "errorText"}
      val anchor = (pane.document as HTMLDocument).getIterator(HTML.Tag.A)
      while (anchor.isValid) {
        if (anchor.attributes.getAttribute(HTML.Attribute.HREF) == Urls.openSdkManager) {
          break
        }
        anchor.next()
      }
      if (!anchor.isValid) {
        fail("Did not find SDK manager link")
      }

      val rect = pane.modelToView2D(anchor.startOffset)
      fakeUi.clickRelativeTo(pane, rect.centerX.toInt(), rect.centerY.toInt())

      pumpAndWait(view.hyperlinkListener.hyperlinkClickTracker.consume())
    }
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckFails() = runBlocking {
    // Prepare
    adbService.useMock = true
      whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(AdbCommandResult(1, listOf(), listOf()))

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      // Assert
      pumpAndWait(view.showDialogTracker.consume());
      pumpAndWait(view.startMdnsCheckTracker.consume());
      pumpAndWait(view.showMdnsCheckErrorTracker.consume());
    }
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckReturnsRandomText() = runBlocking {
    // Prepare
    adbService.useMock = true
    whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(AdbCommandResult(0, listOf("ERROR: mdns daemon unavailable"), listOf()))

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      // Assert
      pumpAndWait(view.showDialogTracker.consume())
      pumpAndWait(view.startMdnsCheckTracker.consume())
      pumpAndWait(view.showMdnsNotSupportedErrorTracker.consume())
    }
  }

  @Test
  fun viewShouldShowQrCodeIfMdnsCheckSucceeds() = runBlocking {
    // Prepare
    adbService.useMock = true
    whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(AdbCommandResult(0, listOf("mdns daemon version [10970003]"), listOf()))

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      // Assert
      pumpAndWait(view.showDialogTracker.consume())
      pumpAndWait(view.startMdnsCheckTracker.consume())
      pumpAndWait(view.showMdnsCheckSuccessTracker.consume())
      pumpAndWait(view.showQrCodePairingStartedTracker.consume())
      val qrCodeImage = pumpAndWait(model.qrCodeImageTracker.consume())
      Truth.assertThat(qrCodeImage).isNotNull()
    }
  }

  /**
   * Summary of this test:
   *
   * * Display a QRCode that encodes a string of the form `"WIFI:T:ADB;S:${generatedServiceName};P:${generatedPassword};;"`.
   *   `generatedServiceName` and `generatedPassword` are random strings generated by Android Studio. The
   *   `generatedServiceName` always starts with the `studio-` prefix to avoid collision with `adb-` prefix.
   *
   * * Simulate the phone scanning the QR code, which results in exposing a mDNS service
   *   of the form `"${generatedServiceName} _adb-tls-pairing._tcp. ${phoneIpAddress}:${phonePairingPort}"`.
   *   The service is discovered by running `"adb mdns services"` in a loop.
   *
   * * Given that ${generatedServiceName} match the service name in the QR code, the controller executes a
   *   `"adb mdns pair ${phoneIpAddress}:${phonePairingPort} ${generatedPassword}"` command through ADB.
   *   This command returns the phone `IP/port` address for connecting (the `port` for connecting is not the same
   *   as the `port` using for pairing) as well as the phone mDNS service name (of the form `"adb-xxxx"`).
   *
   * * After the pair command succeeds, we wait for the phone to show up as a connected device via `"adb devices"`.
   *   We match the phone using the results from the command above (mDNS service name): the phone serial number
   *   contains the mDNS service names of the phone found in the step above (i.e. `"adb-xxxx"`).
   *
   */
  @Test
  fun controllerShouldPairDeviceUsingQrCodeOnceItShowsUp() = runBlocking {
    // Prepare
    randomProvider.seed = 10 // some arbitrary number
    val generatedServiceName = "studio-+8nkUqLWv2" // Depends on seed above
    val generatedPassword = "R7)i3aUHnMnX" // Depends on seed above
    val generatedPairingString = "WIFI:T:ADB;S:${generatedServiceName};P:${generatedPassword};;"
    val phoneIpAddress = "192.168.1.86"
    val phonePairingPort = 37313
    val phoneServiceName = "adb-939AX05XBZ-vWgJpq"
    val phonePairingString = "${generatedServiceName}\t_adb-tls-pairing._tcp.\t${phoneIpAddress}:${phonePairingPort}"
    val phoneConnectPort = 12345
    val phoneDeviceInfo = AdbOnlineDevice("myid", mapOf(
      Pair(IDevice.PROP_DEVICE_MANUFACTURER, "Google"),
      Pair(IDevice.PROP_DEVICE_MODEL, "Pixel 3")))

    adbService.useMock = true
    whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(AdbCommandResult(0, listOf("mdns daemon version [10970003]"), listOf()))

    whenever(adbService.instance.executeCommand(listOf("mdns", "services"), ""))
      .thenReturn(AdbCommandResult(0, listOf(), listOf())) // Simulate user taking some time to scan
      .thenReturn(AdbCommandResult(0, listOf(), listOf())) // Simulate user taking some time to scan
      .thenReturn(AdbCommandResult(0, listOf("List of discovered mdns services", phonePairingString), listOf()))

    whenever(adbService.instance.executeCommand(listOf("pair", "${phoneIpAddress}:${phonePairingPort}"), generatedPassword + newLine()))
      .thenReturn(
        AdbCommandResult(0, listOf("Successfully paired to ${phoneIpAddress}:${phoneConnectPort} [guid=${phoneServiceName}]"), listOf()))

    whenever(adbService.instance.waitForOnlineDevice(any()))
      .thenReturn(phoneDeviceInfo)

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      // Assert
      pumpAndWait(view.showDialogTracker.consume())
      pumpAndWait(view.startMdnsCheckTracker.consume())
      pumpAndWait(view.showMdnsCheckSuccessTracker.consume())
      pumpAndWait(view.showQrCodePairingStartedTracker.consume())

      val qrCodeImage = pumpAndWait(model.qrCodeImageTracker.consume())
      Truth.assertThat(qrCodeImage).isNotNull()
      Truth.assertThat(qrCodeImage?.serviceName).isEqualTo(generatedServiceName)
      Truth.assertThat(qrCodeImage?.password).isEqualTo(generatedPassword)
      Truth.assertThat(qrCodeImage?.pairingString).isEqualTo(generatedPairingString)

      val mdnsService = pumpAndWait(view.showQrCodePairingInProgressTracker.consume())
      Truth.assertThat(mdnsService).isNotNull()
      Truth.assertThat(mdnsService.serviceType).isEqualTo(ServiceType.QrCode)
      Truth.assertThat(mdnsService.serviceName).isEqualTo(generatedServiceName)
      Truth.assertThat(mdnsService.ipAddress.hostAddress).isEqualTo(phoneIpAddress)
      Truth.assertThat(mdnsService.port).isEqualTo(phonePairingPort)

      val pairingResult = pumpAndWait(view.showQrCodePairingWaitForDeviceTracker.consume())
      Truth.assertThat(pairingResult).isNotNull()
      Truth.assertThat(pairingResult.ipAddress.hostAddress).isEqualTo(phoneIpAddress)
      Truth.assertThat(pairingResult.port).isEqualTo(phoneConnectPort)
      Truth.assertThat(pairingResult.mdnsServiceId).isEqualTo(phoneServiceName)

      val (mdnsService2, device) = pumpAndWait(view.showQrCodePairingSuccessTracker.consume())
      Truth.assertThat(mdnsService2).isEqualTo(mdnsService)
      Truth.assertThat(device).isEqualTo(phoneDeviceInfo)

      val (title, content, type, icon) = pumpAndWait(notificationService.showBalloonTracker.consume())
      Truth.assertThat(title).isEqualTo("${device.displayString} connected over Wi-Fi")
      Truth.assertThat(content).isEqualTo("The device is now available to use.")
      Truth.assertThat(type).isEqualTo(NotificationType.INFORMATION)
      Truth.assertThat(icon).isEqualTo(StudioIcons.Common.SUCCESS)
    }
  }

  /**
   * Summary of this test:
   *
   * * Simulate the phone entering a pairing code pairing session, which results in exposing a mDNS service
   *   of the form `"${generatedServiceName} _adb-tls-pairing._tcp. ${phoneIpAddress}:${phonePairingPort}"`.
   *   The service is discovered by running `"adb mdns services"` in a loop.
   *
   * * When the new mDNS service is detected, a new "Pair" panel is shown in the right hand side of the
   *   pairing dialog, with a "Pair" button.
   *
   * * The test simulates clicking that button, which opens a new "Pairing Code Pairing" dialog, then
   *   simulates a 6 digit pairing code in the new dialog. Finally, the test simulates clicking the
   *   "Ok" button in the new dialog, which results in executing a
   *   `"adb mdns pair ${phoneIpAddress}:${phonePairingPort} ${phonePairingCode}"` command through ADB.
   *   This command returns the phone `IP/port` address for connecting (the `port` for connecting is not the same
   *   as the `port` using for pairing) as well as the phone mDNS service name (of the form `"adb-xxxx"`).
   *
   * * After the pair command succeeds, we wait for the phone to show up as a connected device via `"adb devices"`.
   *   We match the phone using the results from the command above (mDNS service name): the phone serial number
   *   contains the mDNS service names of the phone found in the step above (i.e. `"adb-xxxx"`).
   *
   */
  @Test
  fun controllerShouldPairDeviceUsingPairingCodeOnceItShowsUp() = runBlocking {
    // Prepare
    val phoneIpAddress = "192.168.1.86"
    val phonePairingPort = 37313
    val phoneServiceName = "adb-939AX05XBZ-vWgJpq"
    val phonePairingString = "${phoneServiceName}\t_adb-tls-pairing._tcp.\t${phoneIpAddress}:${phonePairingPort}"
    val phonePairingCode = "123456"
    val phoneConnectPort = 12345
    val phoneDeviceInfo = AdbOnlineDevice("myid", mapOf(
      Pair(IDevice.PROP_DEVICE_MANUFACTURER, "Google"),
      Pair(IDevice.PROP_DEVICE_MODEL, "Pixel 3")))

    adbService.useMock = true
    whenever(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(AdbCommandResult(0, listOf("mdns daemon version [10970003]"), listOf()))

    whenever(adbService.instance.executeCommand(listOf("mdns", "services"), ""))
      .thenReturn(AdbCommandResult(0, listOf(), listOf())) // Simulate user taking some time to scan
      .thenReturn(AdbCommandResult(0, listOf(), listOf())) // Simulate user taking some time to scan
      .thenReturn(AdbCommandResult(0, listOf("List of discovered mdns services", phonePairingString), listOf()))

    whenever(adbService.instance.executeCommand(listOf("pair", "${phoneIpAddress}:${phonePairingPort}"), phonePairingCode + newLine()))
      .thenReturn(
        AdbCommandResult(0, listOf("Successfully paired to ${phoneIpAddress}:${phoneConnectPort} [guid=${phoneServiceName}]"), listOf()))

    whenever(adbService.instance.waitForOnlineDevice(any()))
      .thenReturn(phoneDeviceInfo)

    fun enterPairingCode(pairingCodeDialog: DialogWrapper, @Suppress("SameParameterValue") phonePairingCode: String) {
      val pairingView = lastPairingCodeView ?: throw AssertionError("Pairing Code Pairing View show be set")

      val fakeUi = FakeUi(pairingCodeDialog.rootPane)

      // Enter the pairing code
      phonePairingCode.forEachIndexed{ index, ch ->
        // Note: FakeUi keyboard does not emulate focus, so we need to focus each
        //       custom component individually
        fakeUi.keyboard.setFocus(fakeUi.getComponent<JTextField> { c -> c.name == "PairingCode-Digit-${index}" })
        fakeUi.keyboard.type(ch.code)
      }

      // Click the "pair" button
      val okButton = fakeUi.getComponent<JButton> { comp -> comp.text == "Pair" }
      Truth.assertThat(okButton).isNotNull()
      fakeUi.clickOn(okButton)

      // Digits should be disabled once "Pair" is clicked
      for (index in 0..5) {
        val digit = fakeUi.getComponent<JTextField> { c -> c.name == "PairingCode-Digit-${index}" }
        Truth.assertThat(digit.isEnabled).isFalse()
      }

      pumpAndWait(pairingView.showDialogTracker.consume())
      pumpAndWait(pairingView.showPairingInProgressTracker.consume())

      val pairingResult = pumpAndWait(pairingView.showWaitingForDeviceProgressTracker.consume())
      Truth.assertThat(pairingResult.mdnsServiceId).isEqualTo(phoneServiceName)
      Truth.assertThat(pairingResult.ipAddress.hostAddress).isEqualTo(phoneIpAddress)
      Truth.assertThat(pairingResult.port).isEqualTo(phoneConnectPort)

      val (mdnsService, device) = pumpAndWait(pairingView.showPairingSuccessTracker.consume())
      Truth.assertThat(mdnsService).isNotNull()
      Truth.assertThat(mdnsService.serviceType).isEqualTo(ServiceType.PairingCode)
      Truth.assertThat(mdnsService.serviceName).isEqualTo(phoneServiceName)
      Truth.assertThat(mdnsService.ipAddress.hostAddress).isEqualTo(phoneIpAddress)
      Truth.assertThat(mdnsService.port).isEqualTo(phonePairingPort)

      Truth.assertThat(device).isEqualTo(phoneDeviceInfo)

      val (title, content, type, icon) = pumpAndWait(notificationService.showBalloonTracker.consume())
      Truth.assertThat(title).isEqualTo("${device.displayString} connected over Wi-Fi")
      Truth.assertThat(content).isEqualTo("The device is now available to use.")
      Truth.assertThat(type).isEqualTo(NotificationType.INFORMATION)
      Truth.assertThat(icon).isEqualTo(StudioIcons.Common.SUCCESS)
    }

    // Act
    createModalDialogAndInteractWithIt({ controller.showDialog() }) {
      val fakeUi = FakeUi(it.rootPane)

      // Activate the needed page of the JBTabbedPane. Tabs are implemented by JLabel.
      val pairingCodeTab = fakeUi.getComponent<JLabel> { label -> label.text == "Pair using pairing code" }
      fakeUi.clickOn(pairingCodeTab)

      // Assert
      pumpAndWait(view.showDialogTracker.consume())
      pumpAndWait(view.startMdnsCheckTracker.consume())
      pumpAndWait(view.showMdnsCheckSuccessTracker.consume())
      pumpAndWait(view.showQrCodePairingStartedTracker.consume())

      val pairingCodeServices = pumpAndWait(model.pairingCodeServicesTracker.consume())
      Truth.assertThat(pairingCodeServices).isNotNull()
      Truth.assertThat(pairingCodeServices).hasSize(1)
      Truth.assertThat(pairingCodeServices[0].serviceName).isEqualTo(phoneServiceName)
      Truth.assertThat(pairingCodeServices[0].ipAddress.hostAddress).isEqualTo(phoneIpAddress)
      Truth.assertThat(pairingCodeServices[0].port).isEqualTo(phonePairingPort)
      Truth.assertThat(pairingCodeServices[0].serviceType).isEqualTo(ServiceType.PairingCode)

      // We need to layout, since a new panel (for the pairing code device) should have been added
      fakeUi.layout()

      val pairButton = fakeUi.getComponent<JButton> { comp -> comp.text == "Pair" }
      Truth.assertThat(pairButton).isNotNull()
      createModalDialogAndInteractWithIt({ fakeUi.clickOn(pairButton) }) { pairingCodeDialog ->
        enterPairingCode(pairingCodeDialog, phonePairingCode)
      }
    }
  }

  @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
  fun <V> pumpAndWait(future: Future<V>): V {
    return pumpEventsAndWaitForFuture(future, testTimeout.getRemainingUnits(TimeUnit.SECONDS), testTimeUnit)
  }

  private fun newLine(): String {
    return LineSeparator.getSystemLineSeparator().separatorString
  }
}
