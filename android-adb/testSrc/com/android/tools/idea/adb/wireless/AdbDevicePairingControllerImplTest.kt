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

import com.android.ddmlib.TimeoutRemainder
import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.DialogWrapperFactory
import com.android.tools.idea.ui.FakeDialogWrapperRule
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

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

  private val devicePairingService : AdbDevicePairingService by lazy {
    AdbDevicePairingServiceImpl(randomProvider, adbService.instance, taskExecutor)
  }

  private val model: AdbDevicePairingModel by lazy { AdbDevicePairingModel() }

  private val view: MockDevicePairingView by lazy {
    MockDevicePairingView(model)
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
    pumpEventsAndWaitForFuture(view.showDialogTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.startMdnsCheckTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.showMdnsCheckErrorTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
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
    pumpEventsAndWaitForFuture(view.showDialogTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.startMdnsCheckTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.showMdnsNotSupportedByAdbErrorTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
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
    pumpEventsAndWaitForFuture(view.showDialogTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.startMdnsCheckTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.showMdnsNotSupportedErrorTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
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
    pumpEventsAndWaitForFuture(view.showDialogTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.startMdnsCheckTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
    pumpEventsAndWaitForFuture(view.showMdnsCheckSuccessTracker.consume(), testTimeout.remainingUnits, testTimeUnit)
  }
}
