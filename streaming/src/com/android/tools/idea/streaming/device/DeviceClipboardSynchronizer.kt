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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.beans.PropertyChangeListener
import java.io.IOException

/**
 * Synchronizes clipboards between the host and a connected device.
 */
@UiThread
internal class DeviceClipboardSynchronizer(
  disposableParent: Disposable,
  private val deviceClient: DeviceClient
) : CopyPasteManager.ContentChangedListener, DeviceController.DeviceClipboardListener, Disposable {

  private val copyPasteManager = CopyPasteManager.getInstance()
  private var lastClipboardText = ""
  private val deviceController: DeviceController?
    get() = deviceClient.deviceController
  private val focusOwnerListener = PropertyChangeListener { event ->
    // CopyPasteManager.ContentChangedListener doesn't receive notifications for all clipboard
    // changes that happen outside Studio. To compensate for that we also set the device clipboard
    // when Studio gains focus.
    if (event.newValue != null && event.oldValue == null) {
      // Studio gained focus.
      setDeviceClipboard(forceSend = false)
    }
  }

  init {
    Disposer.register(disposableParent, this)
    copyPasteManager.addContentChangedListener(this, this)
    deviceController?.addDeviceClipboardListener(this)
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusOwnerListener)
    setDeviceClipboard(forceSend = true)
  }

  override fun dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusOwnerListener)
    deviceController?.removeDeviceClipboardListener(this)
    deviceController?.sendControlMessage(StopClipboardSyncMessage.instance)
    lastClipboardText = ""
  }

  /**
   * Sets the device clipboard to have the same content as the host clipboard unless the host
   * clipboard is empty and [forceSend] is false.
   */
  fun setDeviceClipboard(forceSend: Boolean) {
    val text = getClipboardText()
    setDeviceClipboard(text, forceSend = forceSend)
  }

  private fun setDeviceClipboard(text: String, forceSend: Boolean) {
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

  private fun getClipboardText(): String {
    return if (copyPasteManager.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      copyPasteManager.getContents(DataFlavor.stringFlavor) ?: ""
    }
    else {
      ""
    }
  }

  @AnyThread
  override fun contentChanged(oldTransferable: Transferable?, newTransferable: Transferable?) {
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
      newTransferable?.getText()?.let { setDeviceClipboard(it, forceSend = false) }
    }
  }

  @AnyThread
  override fun onDeviceClipboardChanged(text: String) {
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
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
    catch (e: UnsupportedFlavorException) {
      null
    }
    catch (e: IOException) {
      null
    }
  }
}