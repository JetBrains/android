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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.beans.PropertyChangeListener
import java.io.IOException

/**
 * Synchronizes clipboards between the host and a connected device.
 */
internal class DeviceClipboardSynchronizer(
  private val deviceController: DeviceController
) : CopyPasteManager.ContentChangedListener, DeviceController.DeviceClipboardListener, Disposable {

  private val copyPasteManager = CopyPasteManager.getInstance()
  private var lastClipboardText = ""
  private val focusOwnerListener = PropertyChangeListener { event ->
    // CopyPasteManager.ContentChangedListener doesn't receive notifications for all clipboard
    // changes that happen outside Studio. To compensate for that we also set the device clipboard
    // when Studio gains focus.
    if (event.newValue != null && event.oldValue == null) {
      // Studio gained focus.
      setDeviceClipboard()
    }
  }

  init {
    Disposer.register(deviceController, this)
    copyPasteManager.addContentChangedListener(this, this)
    deviceController.addDeviceClipboardListener(this)
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusOwnerListener)
    setDeviceClipboard()
  }

  @UiThread
  override fun dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusOwnerListener)
    deviceController.removeDeviceClipboardListener(this)
    val message = StopClipboardSyncMessage()
    deviceController.sendControlMessage(message)
    lastClipboardText = ""
  }

  /**
   * Sets the device clipboard to have the same content as the host clipboard.
   */
  @UiThread
  fun setDeviceClipboard() {
    val text = getClipboardText()
    sendClipboardSyncMessage(text)
  }

  @UiThread
  private fun sendClipboardSyncMessage(text: String) {
    val message = StartClipboardSyncMessage(DeviceMirroringSettings.getInstance().maxSyncedClipboardLength, text)
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

  @UiThread
  override fun contentChanged(oldTransferable: Transferable?, newTransferable: Transferable?) {
    val text = newTransferable?.getText() ?: return
    if (text.isNotEmpty() && text != lastClipboardText) {
      lastClipboardText = text
      sendClipboardSyncMessage(text)
    }
  }

  @AnyThread
  override fun onDeviceClipboardChanged(text: String) {
    EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
      if (text != lastClipboardText) {
        lastClipboardText = text
        copyPasteManager.setContents(StringSelection(text))
      }
    }
  }

  private fun Transferable.getText(): String? {
    return try {
      getTransferData(DataFlavor.stringFlavor) as? String
    }
    catch (e: IOException) {
      null
    }
  }
}