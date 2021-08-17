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
class PairingCodePairingController(edtExecutor: Executor,
                                   private val pairingService: WiFiPairingService,
                                   private val view: PairingCodePairingView) {
  private val LOG = logger<PairingCodePairingController>()
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

  inner class ViewListener : PairingCodePairingView.Listener {
    override fun onPairInvoked() {
      LOG.info("Starting pairing code pairing process with mDNS service ${view.model.service}")
      view.showPairingInProgress()
      val futurePairing = pairingService.pairMdnsService(view.model.service, view.model.pairingCode)
      futurePairing.transform(edtExecutor) { pairingResult ->
        //TODO: Ensure not disposed and state still the same
        view.showWaitingForDeviceProgress(pairingResult)
        pairingResult
      }.transformAsync(edtExecutor) { pairingResult ->
        LOG.info("Pairing code pairing process with mDNS service ${view.model.service} succeeded, now starting to wait for device to connect")
        //TODO: Ensure not disposed and state still the same
        pairingService.waitForDevice(pairingResult)
      }.transform(edtExecutor) { device ->
        LOG.info("Device ${device} corresponding to mDNS service ${view.model.service} is now connected")
        //TODO: Ensure not disposed and state still the same
        view.showPairingSuccess(view.model.service, device)
      }.catching(edtExecutor, Throwable::class.java) { throwable ->
        LOG.warn("Pairing code pairing process failed", throwable)
        //TODO: Ensure not disposed and state still the same
        view.showPairingError(view.model.service, throwable)
      }
    }
  }
}
