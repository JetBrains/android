/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.DataFlavor.stringFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.beans.PropertyChangeListener
import java.io.IOException

/** Synchronizes clipboards between the host and a connected physical or virtual device. */
internal abstract class AbstractClipboardSynchronizer(
  disposableParent: Disposable,
) : CopyPasteManager.ContentChangedListener, Disposable {

  protected var lastClipboardText = ""
  @Volatile protected var isDisposed = false
    private set
  private val copyPasteManager = CopyPasteManager.getInstance()
  private val focusOwnerListener = PropertyChangeListener { event ->
    // CopyPasteManager.ContentChangedListener doesn't receive notifications for all clipboard
    // changes that happen outside Studio. To compensate for that we also set the device clipboard
    // when Studio gains focus.
    if (event.newValue != null && event.oldValue == null) {
      // Studio gained focus.
      synchronizeDeviceClipboard(forceSend = false)
    }
  }

  init {
    Disposer.register(disposableParent, this)
    copyPasteManager.addContentChangedListener(this, this)
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusOwnerListener)
  }

  override fun dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusOwnerListener)
    isDisposed = true
    lastClipboardText = ""
  }

  /**
   * Sets the device clipboard to have the same content as the host clipboard unless the host
   * clipboard is empty and [forceSend] is false.
   */
  @Suppress("WrongThread")
  @AnyThread
  fun synchronizeDeviceClipboard(forceSend: Boolean = false) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      application.executeOnPooledThread { doSynchronizeDeviceClipboard(forceSend) }
    }
    else {
      doSynchronizeDeviceClipboard(forceSend)
    }
  }

  @WorkerThread
  private fun doSynchronizeDeviceClipboard(forceSend: Boolean) {
    val text = copyPasteManager.getContents(stringFlavor) ?: ""
    if (forceSend || text.isNotEmpty()) {
      EventQueue.invokeLater {
        setDeviceClipboard(text, forceSend = forceSend)
      }
    }
  }

  @UiThread
  abstract fun setDeviceClipboard(text: String, forceSend: Boolean)

  @AnyThread
  override fun contentChanged(oldTransferable: Transferable?, newTransferable: Transferable?) {
    UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
      newTransferable?.getText()?.let { setDeviceClipboard(it, forceSend = false) }
    }
  }

  @AnyThread
  fun onDeviceClipboardChanged(text: String) {
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