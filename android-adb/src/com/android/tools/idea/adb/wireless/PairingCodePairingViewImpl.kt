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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo

@UiThread
class PairingCodePairingViewImpl(
  project: Project,
  private val notificationService: WiFiPairingNotificationService,
  override val model: PairingCodePairingModel,
) : PairingCodePairingView {
  private val dlg = PairingCodePairingDialog(project)
  private val listeners = ArrayList<PairingCodePairingView.Listener>()
  private var allowPairAction = true

  override fun showDialog() {
    dlg.setDevice(model.service)
    dlg.validationHandler = {
      if (dlg.isPairingCodeValid) {
        null
      } else {
        ValidationInfo("Pairing code must be exactly 6 digits", dlg.pairingCodeComponent)
      }
    }
    dlg.okButtonHandler = {
      if (allowPairAction) {
        model.pairingCode = dlg.currentPairingCode
        listeners.forEach { it.onPairInvoked() }
        true
      } else {
        false
      }
    }
    dlg.show()
  }

  override fun addListener(listener: PairingCodePairingView.Listener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PairingCodePairingView.Listener) {
    listeners.remove(listener)
  }

  override fun showPairingInProgress() {
    dlg.showPairingInProgress("Pairing with device...")
    allowPairAction = false
  }

  override fun showWaitingForDeviceProgress(pairingResult: PairingResult) {
    dlg.showPairingInProgress("Connecting to device. This takes up to 2 minutes.")
    allowPairAction = false
  }

  override fun showPairingSuccess(service: MdnsService, device: AdbOnlineDevice) {
    dlg.showPairingSuccess(device)
    allowPairAction = false
    notificationService.showPairingSuccessBalloon(device)
  }

  override fun showPairingError(service: MdnsService, error: Throwable) {
    dlg.showPairingError()
    allowPairAction = true
  }
}
