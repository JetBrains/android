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
import java.awt.image.BufferedImage
import java.util.ArrayList

/**
 * Model used for pairing devices
 */
@UiThread
class AdbDevicePairingModel {
  /** The list of listeners */
  private val listeners: ArrayList<AdbDevicePairingModelListener> = ArrayList<AdbDevicePairingModelListener>()

  /**
   * The last [QrCodeImage] generated. It may be `null` if no image has been generated yet.
   */
  var qrCodeImage : QrCodeImage? = null
    set(value) {
      field = value
      value?.let { newImage ->
        listeners.forEach { it.qrCodeGenerated(newImage) }
      }
    }

  fun addListener(listener: AdbDevicePairingModelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: AdbDevicePairingModelListener) {
    listeners.remove(listener)
  }
}

@UiThread
interface AdbDevicePairingModelListener {
  /**
   * Invoked when a new QrCode image has been generated
   */
  fun qrCodeGenerated(newImage: QrCodeImage)
}
