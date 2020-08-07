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
import java.util.ArrayList

class MockDevicePairingView(override val model: AdbDevicePairingModel) : AdbDevicePairingView {
  private val myListeners = ArrayList<AdbDevicePairingView.Listener>()
  val showDialogTracker = FutureValuesTracker<Unit>()
  val startMdnsCheckTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckSuccessTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsNotSupportedByAdbErrorTracker = FutureValuesTracker<Unit>()
  val showMdnsCheckErrorTracker = FutureValuesTracker<Unit>()

  override fun showDialog() {
    showDialogTracker.produce(Unit)
  }

  override fun startMdnsCheck() {
    startMdnsCheckTracker.produce(Unit)
  }

  override fun showMdnsCheckSuccess() {
    showMdnsCheckSuccessTracker.produce(Unit)
  }

  override fun showMdnsNotSupportedError() {
    showMdnsNotSupportedErrorTracker.produce(Unit)
  }

  override fun showMdnsNotSupportedByAdbError() {
    showMdnsNotSupportedByAdbErrorTracker.produce(Unit)
  }

  override fun showMdnsCheckError() {
    showMdnsCheckErrorTracker.produce(Unit)
  }

  override fun showQrCodePairingStarted() {
  }

  override fun showQrCodePairingInProgress(mdnsService: MdnsService) {
  }

  override fun showQrCodePairingWaitForDevice(pairingResult: PairingResult) {
  }

  override fun showQrCodePairingSuccess(mdnsService: MdnsService, device: AdbOnlineDevice) {
  }

  override fun showQrCodePairingError(mdnsService: MdnsService, error: Throwable) {
  }

  override fun addListener(listener: AdbDevicePairingView.Listener) {
    myListeners.add(listener)
  }

  override fun removeListener(listener: AdbDevicePairingView.Listener) {
    myListeners.remove(listener)
  }
}