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

class MockPinCodePairingView(val project: Project, override val model: PinCodePairingModel) : PinCodePairingView {
  private val viewImpl = PinCodePairingViewImpl(project, model)

  val showDialogTracker = FutureValuesTracker<Unit>()
  val showPairingInProgressTracker = FutureValuesTracker<Unit>()
  val showWaitingForDeviceProgressTracker = FutureValuesTracker<PairingResult>()
  val showPairingSuccessTracker = FutureValuesTracker<Pair<MdnsService, AdbOnlineDevice>>()
  val showPairingErrorTracker = FutureValuesTracker<Pair<MdnsService, Throwable>>()

  override fun showDialog() {
    showDialogTracker.produce(Unit)
    viewImpl.showDialog()
  }

  override fun showPairingInProgress() {
    showPairingInProgressTracker.produce(Unit)
    viewImpl.showPairingInProgress()
  }

  override fun showWaitingForDeviceProgress(pairingResult: PairingResult) {
    showWaitingForDeviceProgressTracker.produce(pairingResult)
    viewImpl.showWaitingForDeviceProgress(pairingResult)
  }

  override fun showPairingSuccess(service: MdnsService, device: AdbOnlineDevice) {
    showPairingSuccessTracker.produce(Pair(service, device))
    viewImpl.showPairingSuccess(service, device)
  }

  override fun showPairingError(service: MdnsService, error: Throwable) {
    showPairingErrorTracker.produce(Pair(service, error))
    viewImpl.showPairingError(service, error)
  }

  override fun addListener(listener: PinCodePairingView.Listener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: PinCodePairingView.Listener) {
    viewImpl.removeListener(listener)
  }
}
