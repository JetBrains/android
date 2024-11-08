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
import com.android.tools.idea.streaming.core.AbstractClipboardSynchronizer
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Synchronizes the AVD and the host clipboards.
 */
internal class EmulatorClipboardSynchronizer(
  disposableParent: Disposable,
  val emulator: EmulatorController,
) : AbstractClipboardSynchronizer(disposableParent) {

  @GuardedBy("lock")
  private var clipboardFeed: Cancelable? = null
  @GuardedBy("lock")
  private var clipboardReceiver: ClipboardReceiver? = null
  private val lock = Any()

  private val logger
    get() = thisLogger()

  init {
    synchronizeDeviceClipboard(forceSend = true)
  }

  override fun dispose() {
    synchronized(lock) {
      cancelClipboardFeed()
    }
    super.dispose()
  }

  @UiThread
  override fun setDeviceClipboard(text: String, forceSend: Boolean) {
    if (isDisposed) {
      return
    }
    if (text.isNotEmpty() && text != lastClipboardText) {
      lastClipboardText = text
      logger.debug { "EmulatorClipboardSynchronizer.setDeviceClipboard: \"$text\"" }
      emulator.setClipboard(ClipData.newBuilder().setText(text).build(), object : EmptyStreamObserver<Empty>() {
        override fun onCompleted() {
          if (!isDisposed) {
            requestClipboardFeed()
          }
        }
      })
    }
    else if (clipboardFeed == null) {
      requestClipboardFeed()
    }
  }

  @GuardedBy("lock")
  private fun cancelClipboardFeed() {
    clipboardReceiver = null
    clipboardFeed?.cancel()
    clipboardFeed = null
  }

  private fun requestClipboardFeed() {
    synchronized(lock) {
      cancelClipboardFeed()
      if (emulator.connectionState == EmulatorController.ConnectionState.CONNECTED) {
        val receiver = ClipboardReceiver()
        clipboardReceiver = receiver
        clipboardFeed = emulator.streamClipboard(receiver)
      }
    }
  }

  private inner class ClipboardReceiver : EmptyStreamObserver<ClipData>() {

    override fun onNext(message: ClipData) {
      logger.debug { "ClipboardReceiver.onNext: \"${message.text}\"" }
      synchronized(lock) {
        if (clipboardReceiver != this) {
          return // This clipboard feed has already been cancelled.
        }
      }
      if (message.text.isNotEmpty()) {
        onDeviceClipboardChanged(message.text)
      }
    }

    override fun onError(t: Throwable) {
      if (t is EmulatorController.RetryException) {
        requestClipboardFeed()
      }
    }
  }
}