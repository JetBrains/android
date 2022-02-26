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
package com.android.tools.idea.device

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import java.awt.EventQueue
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.IOException

/**
 * Synchronizes clipboards between the host and a connected device.
 */
internal class DeviceClipboardSynchronizer(
  private val deviceController: DeviceController,
  parentDisposable: Disposable
) : CopyPasteManager.ContentChangedListener, DeviceController.DeviceClipboardListener, Disposable {

  private val copyPasteManager = CopyPasteManager.getInstance()

  init {
    Disposer.register(parentDisposable, this)
    copyPasteManager.addContentChangedListener(this, this)
  }

  @UiThread
  override fun dispose() {
    stopKeepingHostClipboardInSync()
  }

  @UiThread
  fun setDeviceClipboardAndKeepHostClipboardInSync() {
    deviceController.addDeviceClipboardListener(this)
    val text = getClipboardText()
    sendClipboardSyncMessage(text)
  }

  private fun sendClipboardSyncMessage(text: String) {
    val message = StartClipboardSyncMessage(MAX_SYNCED_CLIPBOARD_LENGTH, text)
    deviceController.sendControlMessage(message)
  }

  @UiThread
  private fun stopKeepingHostClipboardInSync() {
    deviceController.removeDeviceClipboardListener(this)
    val message = StopClipboardSyncMessage()
    deviceController.sendControlMessage(message)
  }

  @UiThread
  private fun getClipboardText(): String {
    return if (copyPasteManager.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      copyPasteManager.getContents(DataFlavor.stringFlavor) ?: ""
    }
    else {
      ""
    }
  }

  override fun contentChanged(oldTransferable: Transferable?, newTransferable: Transferable?) {
    val text = try {
      newTransferable?.getTransferData(DataFlavor.stringFlavor) as? String ?: return
    }
    catch (e: IOException) {
      return
    }
    if (text.isNotEmpty()) {
      sendClipboardSyncMessage(text)
    }
  }

  override fun onDeviceClipboardChanged(text: String) {
    EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
      copyPasteManager.setContents(StringSelection(text))
    }
  }
}

/** Max length of clipboard text to participate in clipboard synchronization. */
// TODO: Make configurable.
private const val MAX_SYNCED_CLIPBOARD_LENGTH = 4096