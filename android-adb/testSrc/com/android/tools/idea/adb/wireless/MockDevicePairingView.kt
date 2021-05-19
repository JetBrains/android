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

class MockDevicePairingView(val project: Project, override val model: AdbDevicePairingModel) : AdbDevicePairingView {
  private val viewImpl = AdbDevicePairingViewImpl(project, model)
  val showDialogTracker = FutureValuesTracker<Unit>()
  val startMdnsCheckTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckSuccessTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedByAdbErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckErrorTracker = FutureValuesTracker<Unit>()
  val showQrCodePairingStartedTracker = FutureValuesTracker<Unit>()
  val showQrCodePairingInProgressTracker = FutureValuesTracker<MdnsService>()
  val showQrCodePairingWaitForDeviceTracker = FutureValuesTracker<PairingResult>()
  val showQrCodePairingSuccessTracker = FutureValuesTracker<Pair<MdnsService,AdbOnlineDevice>>()
  val showQrCodePairingErrorTracker = FutureValuesTracker<Pair<MdnsService,Throwable>>()

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
    viewImpl.showMdnsCheckSuccess()
  }

  override fun showQrCodePairingStarted() {
    showQrCodePairingStartedTracker.produce(Unit)
    viewImpl.showQrCodePairingStarted()
  }

  override fun showQrCodePairingInProgress(mdnsService: MdnsService) {
    showQrCodePairingInProgressTracker.produce(mdnsService)
    viewImpl.showQrCodePairingInProgress(mdnsService)
  }

  override fun showQrCodePairingWaitForDevice(pairingResult: PairingResult) {
    showQrCodePairingWaitForDeviceTracker.produce(pairingResult)
    viewImpl.showQrCodePairingWaitForDevice(pairingResult)
  }

  override fun showQrCodePairingSuccess(mdnsService: MdnsService, device: AdbOnlineDevice) {
    showQrCodePairingSuccessTracker.produce(Pair(mdnsService, device))
    viewImpl.showQrCodePairingSuccess(mdnsService, device)
  }

  override fun showQrCodePairingError(mdnsService: MdnsService, error: Throwable) {
    showQrCodePairingErrorTracker.produce(Pair(mdnsService, error))
    viewImpl.showQrCodePairingError(mdnsService, error)
  }

  override fun addListener(listener: AdbDevicePairingView.Listener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: AdbDevicePairingView.Listener) {
    viewImpl.removeListener(listener)
  }
}
