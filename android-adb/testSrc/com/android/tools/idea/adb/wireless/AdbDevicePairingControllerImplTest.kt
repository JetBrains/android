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
import com.android.flags.junit.RestoreFlagRule
import com.android.testutils.MockitoKt.any
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.DialogWrapperFactory
import com.android.tools.idea.ui.FakeDialogWrapperRule
import com.google.common.truth.Truth
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.LineSeparator
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.net.InetAddress
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AdbDevicePairingControllerImplTest : LightPlatform4TestCase() {
  /** Ensures feature flag is reset after test */
  @get:Rule
  val restoreFlagRule = RestoreFlagRule(StudioFlags.ADB_WIRELESS_PAIRING_ENABLED)

  /** Use [DialogWrapperFactory] that is compatible with unit tests */
  @get:Rule
  val testingDialogWrapperRule = FakeDialogWrapperRule()

  private val edtExecutor by lazy { EdtExecutorService.getInstance() }

  private val taskExecutor by lazy { AppExecutorUtil.getAppExecutorService() }

  private val timeProvider: MockNanoTimeProvider by lazy { MockNanoTimeProvider() }

  private val randomProvider by lazy { MockRandomProvider() }

  private val adbService = mockOrActual<AdbServiceWrapper> {
    AdbServiceWrapperImpl(project, timeProvider, MoreExecutors.listeningDecorator(taskExecutor))
  }

  private val devicePairingService: AdbDevicePairingService by lazy {
    AdbDevicePairingServiceImpl(randomProvider, adbService.instance, taskExecutor)
  }

  private val model: MockAdbDevicePairingModel by lazy { MockAdbDevicePairingModel() }

  private val view: MockDevicePairingView by lazy {
    MockDevicePairingView(project, model)
  }

  private val controller: AdbDevicePairingControllerImpl by lazy {
    AdbDevicePairingControllerImpl(project, edtExecutor, devicePairingService, view)
  }

  private val testTimeUnit = TimeUnit.SECONDS
  private val testTimeout = TimeoutRemainder(30, testTimeUnit)

  @Test
  fun viewShouldShowErrorIfAdbPathIsNotSet() {
    // Prepare

    // Act
    controller.showDialog()

    // Assert
    pumpAndWait(view.showDialogTracker.consume())
    pumpAndWait(view.startMdnsCheckTracker.consume())
    pumpAndWait(view.showMdnsCheckErrorTracker.consume())
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckIsNotSupported() {
    // Prepare
    adbService.useMock = true
    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(1, listOf(), listOf("unknown command"))))

    // Act
    controller.showDialog()

    // Assert
    pumpAndWait(view.showDialogTracker.consume())
    pumpAndWait(view.startMdnsCheckTracker.consume())
    pumpAndWait(view.showMdnsNotSupportedByAdbErrorTracker.consume())
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckFails() {
    // Prepare
    adbService.useMock = true
    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(1, listOf(), listOf())))

    // Act
    controller.showDialog()

    // Assert
    pumpEventsAndWaitForFuture(view.showDialogTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.startMdnsCheckTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.showMdnsCheckErrorTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
  }

  @Test
  fun viewShouldShowErrorIfMdnsCheckReturnsRandomText() {
    // Prepare
    adbService.useMock = true
    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf("ERROR: mdns daemon unavailable"), listOf())))

    // Act
    controller.showDialog()

    // Assert
    pumpAndWait(view.showDialogTracker.consume())
    pumpAndWait(view.startMdnsCheckTracker.consume())
    pumpAndWait(view.showMdnsNotSupportedErrorTracker.consume())
  }

  @Test
  fun viewShouldShowQrCodeIfMdnsCheckSucceeds() {
    // Prepare
    adbService.useMock = true
    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf("mdns daemon version [10970003]"), listOf())))

    // Act
    controller.showDialog()

    // Assert
    pumpAndWait(view.showDialogTracker.consume())
    pumpAndWait(view.startMdnsCheckTracker.consume())
    pumpAndWait(view.showMdnsCheckSuccessTracker.consume())
    pumpAndWait(view.showQrCodePairingStartedTracker.consume())
    val qrCodeImage = pumpAndWait(model.qrCodeImageTracker.consume())
    Truth.assertThat(qrCodeImage).isNotNull()
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
  fun controllerShouldPairDeviceUsingQrCodeOnceItShowsUp() {
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
    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "check"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf("mdns daemon version [10970003]"), listOf())))

    Mockito
      .`when`(adbService.instance.executeCommand(listOf("mdns", "services"), ""))
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf(), listOf()))) // Simulate user taking some time to scan
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf(), listOf()))) // Simulate user taking some time to scan
      .thenReturn(Futures.immediateFuture(AdbCommandResult(0, listOf("List of discovered mdns services", phonePairingString), listOf())))

    Mockito
      .`when`(adbService.instance.executeCommand(listOf("pair", "${phoneIpAddress}:${phonePairingPort}"), generatedPassword + newLine()))
      .thenReturn(Futures.immediateFuture(
        AdbCommandResult(0, listOf("Successfully paired to ${phoneIpAddress}:${phoneConnectPort} [guid=${phoneServiceName}]"), listOf())))

    Mockito
      .`when`(adbService.instance.waitForOnlineDevice(any()))
      .thenReturn(Futures.immediateFuture(phoneDeviceInfo))

    // Act
    controller.showDialog()

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
    Truth.assertThat(mdnsService.ipAddress).isEqualTo(InetAddress.getByName(phoneIpAddress))
    Truth.assertThat(mdnsService.port).isEqualTo(phonePairingPort)

    val pairingResult = pumpAndWait(view.showQrCodePairingWaitForDeviceTracker.consume())
    Truth.assertThat(pairingResult).isNotNull()
    Truth.assertThat(pairingResult.ipAddress).isEqualTo(InetAddress.getByName(phoneIpAddress))
    Truth.assertThat(pairingResult.port).isEqualTo(phoneConnectPort)
    Truth.assertThat(pairingResult.mdnsServiceId).isEqualTo(phoneServiceName)

    val pairingSuccess = pumpAndWait(view.showQrCodePairingSuccessTracker.consume())
    Truth.assertThat(pairingSuccess).isNotNull()
    Truth.assertThat(pairingSuccess.second).isEqualTo(phoneDeviceInfo)
  }


  @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
  fun <V> pumpAndWait(future: Future<V>): V {
    return pumpEventsAndWaitForFuture(future, testTimeout.remainingUnits, testTimeUnit)
  }

  private fun newLine(): String {
    return LineSeparator.getSystemLineSeparator().separatorString
  }
}
