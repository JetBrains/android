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
import com.android.tools.idea.FutureValuesTracker

/**
 * Model used for pairing devices
 */
@UiThread
class MockAdbDevicePairingModel : AdbDevicePairingModel() {
  val qrCodeServicesTracker = FutureValuesTracker<List<MdnsService>>()
  val pinCodeServicesTracker = FutureValuesTracker<List<MdnsService>>()
  val qrCodeImageTracker = FutureValuesTracker<QrCodeImage?>()

  override var qrCodeServices: List<MdnsService>
    get() = super.qrCodeServices
    set(value) {
      super.qrCodeServices = value
      qrCodeServicesTracker.produce(value)
    }

  override var pinCodeServices: List<MdnsService>
    get() = super.pinCodeServices
    set(value) {
      super.pinCodeServices = value
      pinCodeServicesTracker.produce(value)
    }

  override var qrCodeImage: QrCodeImage?
    get() = super.qrCodeImage
    set(value) {
      super.qrCodeImage = value
      qrCodeImageTracker.produce(value)
    }
}
