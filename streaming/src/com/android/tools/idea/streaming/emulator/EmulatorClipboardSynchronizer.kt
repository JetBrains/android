/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ClipData
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.protobuf.Empty
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import java.awt.EventQueue
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Synchronizes the AVD and the host clipboards.
 */
internal class EmulatorClipboardSynchronizer(val emulator: EmulatorController, parentDisposable: Disposable) : Disposable {

  private val copyPasteManager = CopyPasteManager.getInstance()
  @GuardedBy("lock")
  private var clipboardFeed: Cancelable? = null
  @GuardedBy("lock")
  private var clipboardReceiver: ClipboardReceiver? = null
  @GuardedBy("lock")
  private var active = false
  private val lock = Any()

  private val connected
    get() = emulator.connectionState == EmulatorController.ConnectionState.CONNECTED

  private var lastClipboardText = ""

  private val logger
    get() = thisLogger()

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    cancelClipboardFeed()
  }

  @UiThread
  fun setDeviceClipboardAndKeepHostClipboardInSync() {
    synchronized(lock) {
      active = true
    }
    val text = getClipboardText()
    if (text.isEmpty() || text == lastClipboardText) {
      requestClipboardFeed()
    }
    else {
      lastClipboardText = text
      logger.debug { "EmulatorClipboardSynchronizer.setDeviceClipboardAndKeepHostClipboardInSync: \"$text\"" }
      emulator.setClipboard(ClipData.newBuilder().setText(text).build(), object : EmptyStreamObserver<Empty>() {
        override fun onCompleted() {
          requestClipboardFeed()
        }
      })
    }
  }

  @UiThread
  fun stopKeepingHostClipboardInSync() {
    synchronized(lock) {
      active = false
      cancelClipboardFeed()
      lastClipboardText = ""
    }
  }

  private fun cancelClipboardFeed() {
    clipboardReceiver = null
    clipboardFeed?.cancel()
    clipboardFeed = null
  }

  private fun requestClipboardFeed() {
    synchronized(lock) {
      if (active) {
        cancelClipboardFeed()
        if (connected) {
          val receiver = ClipboardReceiver()
          clipboardReceiver = receiver
          clipboardFeed = emulator.streamClipboard(receiver)
        }
      }
    }
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

  private inner class ClipboardReceiver : EmptyStreamObserver<ClipData>() {

    override fun onNext(message: ClipData) {
      logger.debug { "ClipboardReceiver.onNext: \"${message.text}\"" }
      if (clipboardReceiver != this) {
        return // This clipboard feed has already been cancelled.
      }

      if (message.text.isNotEmpty()) {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          if (message.text != lastClipboardText) {
            lastClipboardText = message.text
            val content = StringSelection(message.text)
            ClipboardSynchronizer.getInstance().setContent(content, content)
          }
        }
      }
    }
  }
}