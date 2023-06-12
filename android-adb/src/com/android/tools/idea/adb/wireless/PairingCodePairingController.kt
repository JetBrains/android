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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UiThread
class PairingCodePairingController(private val scope: CoroutineScope,
                                   private val pairingService: WiFiPairingService,
                                   private val view: PairingCodePairingView) {
  private val LOG = logger<PairingCodePairingController>()

  init {
    view.addListener(ViewListener())
  }

  /**
   * Note: This call is blocking, as it displays a modal dialog
   */
  fun showDialog() {
    view.showDialog()
  }

  @UiThread
  inner class ViewListener : PairingCodePairingView.Listener {
    override fun onPairInvoked() {
      LOG.info("Starting pairing code pairing process with mDNS service ${view.model.service}")
      view.showPairingInProgress()

      scope.launch(uiThread(ModalityState.any())) {
        try {
          val pairingResult = pairingService.pairMdnsService(view.model.service, view.model.pairingCode)
          view.showWaitingForDeviceProgress(pairingResult)
          LOG.info(
            "Pairing code pairing process with mDNS service ${view.model.service} succeeded, now starting to wait for device to connect")
          //TODO: Ensure not disposed and state still the same
          val device = pairingService.waitForDevice(pairingResult)
          LOG.info("Device ${device} corresponding to mDNS service ${view.model.service} is now connected")
          //TODO: Ensure not disposed and state still the same
          view.showPairingSuccess(view.model.service, device)
        } catch (e: Throwable) {
          LOG.warn("Pairing code pairing process failed", e)
          //TODO: Ensure not disposed and state still the same
          view.showPairingError(view.model.service, e)
        }
      }
    }
  }
}
