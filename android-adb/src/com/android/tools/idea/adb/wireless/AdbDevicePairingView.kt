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

/**
 * Definition of the view (in the context of a Model-View-Controller pattern) used to pair devices
 */
@UiThread
interface AdbDevicePairingView {
  val model: AdbDevicePairingModel

  fun showDialog()

  //region mDNS support functions
  fun startMdnsCheck()
  fun showMdnsCheckSuccess()
  fun showMdnsNotSupportedError()
  fun showMdnsNotSupportedByAdbError()
  fun showMdnsCheckError()
  //endregion

  fun showQrCodePairingStarted()
  fun showQrCodePairingInProgress(mdnsService: MdnsService)
  fun showQrCodePairingWaitForDevice(pairingResult: PairingResult)
  fun showQrCodePairingSuccess(mdnsService: MdnsService, device: AdbOnlineDevice)
  fun showQrCodePairingError(mdnsService: MdnsService, error: Throwable)

  fun addListener(listener: Listener)
  fun removeListener(listener: Listener)

  @UiThread
  interface Listener {
    fun onScanAnotherQrCodeDeviceAction()
    fun onPinCodePairAction(mdnsService: MdnsService)
    fun onClose()
  }
}
