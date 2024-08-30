/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.core.AbstractClipboardSynchronizer
import com.intellij.openapi.Disposable

/**
 * Synchronizes clipboards between the host and a connected device.
 */
internal class DeviceClipboardSynchronizer(
  disposableParent: Disposable,
  private val deviceClient: DeviceClient,
) : AbstractClipboardSynchronizer(disposableParent), DeviceController.DeviceClipboardListener, Disposable {

  private val deviceController: DeviceController?
    get() = deviceClient.deviceController

  init {
    deviceController?.addDeviceClipboardListener(this)
    // Pass the new value of maxSyncedClipboardLength to the device.
    setDeviceClipboard(forceSend = true)
  }

  override fun dispose() {
    deviceController?.removeDeviceClipboardListener(this)
    deviceController?.sendControlMessage(StopClipboardSyncMessage.instance)
    super.dispose()
  }

  @UiThread
  override fun setDeviceClipboard(text: String, forceSend: Boolean) {
    if (isDisposed) {
      return
    }
    val maxSyncedClipboardLength = DeviceMirroringSettings.getInstance().maxSyncedClipboardLength
    if (forceSend || (text.isNotEmpty() && text != lastClipboardText)) {
      val adjustedText = when {
        text.length <= maxSyncedClipboardLength -> text
        forceSend -> ""
        else -> return
      }
      val message = StartClipboardSyncMessage(maxSyncedClipboardLength, adjustedText)
      deviceController?.sendControlMessage(message)
      lastClipboardText = adjustedText
    }
  }
}