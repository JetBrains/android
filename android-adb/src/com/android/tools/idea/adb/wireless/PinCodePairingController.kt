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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.Executor

@UiThread
class PinCodePairingController(edtExecutor: Executor,
                               private val pairingService: AdbDevicePairingService,
                               private val view: PinCodePairingView) {
  private val LOG = logger<PinCodePairingController>()
  private val edtExecutor = FutureCallbackExecutor.wrap(edtExecutor)

  init {
    view.addListener(ViewListener())
  }

  /**
   * Note: This call is blocking, as it displays a modal dialog
   */
  fun showDialog() {
    view.showDialog()
  }

  inner class ViewListener : PinCodePairingView.Listener {
    override fun onPairInvoked() {
      LOG.info("Starting pin code pairing process with mDNS service ${view.model.service}")
      view.showPairingInProgress()
      val futurePairing = pairingService.pairMdnsService(view.model.service, view.model.pinCode)
      futurePairing.transform(edtExecutor) { pairingResult ->
        //TODO: Ensure not disposed and state still the same
        view.showWaitingForDeviceProgress(pairingResult)
        pairingResult
      }.transformAsync(edtExecutor) { pairingResult ->
        LOG.info("Pin code pairing process with mDNS service ${view.model.service} succeeded, now starting to wait for device to connect")
        //TODO: Ensure not disposed and state still the same
        pairingService.waitForDevice(pairingResult)
      }.transform(edtExecutor) { device ->
        LOG.info("Device ${device} corresponding to mDNS service ${view.model.service} is now connected")
        //TODO: Ensure not disposed and state still the same
        view.showPairingSuccess(view.model.service, device)
      }.catching(edtExecutor, Throwable::class.java) { throwable ->
        LOG.warn("Pin code pairing process failed", throwable)
        //TODO: Ensure not disposed and state still the same
        view.showPairingError(view.model.service, throwable)
      }
    }
  }
}
