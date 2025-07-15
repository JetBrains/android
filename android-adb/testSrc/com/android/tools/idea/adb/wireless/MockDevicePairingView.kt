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

import com.android.tools.idea.FutureValuesTracker
import com.intellij.openapi.project.Project

class MockDevicePairingView(
  project: Project,
  notificationService: WiFiPairingNotificationService,
  override val model: WiFiPairingModel,
) : WiFiPairingView {
  val hyperlinkListener = MockWiFiPairingHyperlinkListener()
  private val viewImpl =
    WiFiPairingViewImpl(project, notificationService, model, hyperlinkListener, null)
  val showDialogTracker = FutureValuesTracker<Unit>()
  val startMdnsCheckTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckSuccessTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedByAdbErrorTracker = FutureValuesTracker<Unit>()
  val showMacWontWorkAdbErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckErrorTracker = FutureValuesTracker<Unit>()
  val showQrCodePairingStartedTracker = FutureValuesTracker<Unit>()
  val showQrCodePairingInProgressTracker = FutureValuesTracker<PairingMdnsService>()
  val showQrCodePairingWaitForDeviceTracker = FutureValuesTracker<PairingResult>()
  val showQrCodePairingSuccessTracker =
    FutureValuesTracker<Pair<PairingMdnsService, AdbOnlineDevice>>()
  val showQrCodePairingErrorTracker = FutureValuesTracker<Pair<PairingMdnsService, Throwable>>()
  val showMdnsDisabledOnAdbServer = FutureValuesTracker<PairingMdnsService>()

  override fun showDialog() {
    showDialogTracker.produce(Unit)
    viewImpl.showDialog()
  }

  override fun startMdnsCheck() {
    startMdnsCheckTracker.produce(Unit)
    viewImpl.startMdnsCheck()
  }

  override fun showMdnsCheckSuccess() {
    showMdnsCheckSuccessTracker.produce(Unit)
    viewImpl.showMdnsCheckSuccess()
  }

  override fun showMdnsNotSupportedError() {
    showMdnsNotSupportedErrorTracker.produce(Unit)
    viewImpl.showMdnsNotSupportedError()
  }

  override fun showMdnsNotSupportedByAdbError() {
    showMdnsNotSupportedByAdbErrorTracker.produce(Unit)
    viewImpl.showMdnsNotSupportedByAdbError()
  }

  override fun showMdnsCheckError() {
    showMdnsCheckErrorTracker.produce(Unit)
    viewImpl.showMdnsCheckError()
  }

  override fun showQrCodePairingStarted() {
    showQrCodePairingStartedTracker.produce(Unit)
    viewImpl.showQrCodePairingStarted()
  }

  override fun showQrCodePairingInProgress(pairingMdnsService: PairingMdnsService) {
    showQrCodePairingInProgressTracker.produce(pairingMdnsService)
    viewImpl.showQrCodePairingInProgress(pairingMdnsService)
  }

  override fun showQrCodePairingWaitForDevice(pairingResult: PairingResult) {
    showQrCodePairingWaitForDeviceTracker.produce(pairingResult)
    viewImpl.showQrCodePairingWaitForDevice(pairingResult)
  }

  override fun showQrCodePairingSuccess(
    pairingMdnsService: PairingMdnsService,
    device: AdbOnlineDevice,
  ) {
    showQrCodePairingSuccessTracker.produce(Pair(pairingMdnsService, device))
    viewImpl.showQrCodePairingSuccess(pairingMdnsService, device)
  }

  override fun showQrCodePairingError(pairingMdnsService: PairingMdnsService, error: Throwable) {
    showQrCodePairingErrorTracker.produce(Pair(pairingMdnsService, error))
    viewImpl.showQrCodePairingError(pairingMdnsService, error)
  }

  override fun showMacMdnsEnvironmentIsBroken() {
    showMacWontWorkAdbErrorTracker.produce(Unit)
    viewImpl.showMacMdnsEnvironmentIsBroken()
  }

  override fun showMdnsDisabledOnAdbServer() {
    viewImpl.showMdnsDisabledOnAdbServer()
  }

  override fun addListener(listener: WiFiPairingView.Listener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: WiFiPairingView.Listener) {
    viewImpl.removeListener(listener)
  }
}
