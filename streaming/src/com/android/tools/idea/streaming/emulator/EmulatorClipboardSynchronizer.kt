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
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.streaming.core.AbstractClipboardSynchronizer
import com.android.utils.throwIfCancellation
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Synchronizes the AVD and the host clipboards. */
internal class EmulatorClipboardSynchronizer(disposableParent: Disposable, val emulator: EmulatorController) :
  AbstractClipboardSynchronizer(disposableParent) {

  private val coroutineScope = createCoroutineScope()
  @GuardedBy("lock") private var clipboardReceiver: Job? = null
  private val lock = Any()

  private val logger
    get() = thisLogger()

  init {
    synchronizeDeviceClipboard(forceSend = true)
  }

  override fun dispose() {
    synchronized(lock) { cancelClipboardFeed() }
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
      coroutineScope.launch {
        try {
          emulator.setClipboard(ClipData.newBuilder().setText(text).build())
          if (!isDisposed) {
            requestClipboardFeed()
          }
        } catch (e: Throwable) {
          e.throwIfCancellation()
          // gRPC exceptions are already logged.
        }
      }
    } else if (clipboardReceiver == null) {
      requestClipboardFeed()
    }
  }

  @GuardedBy("lock")
  private fun cancelClipboardFeed() {
    clipboardReceiver?.cancel()
    clipboardReceiver = null
  }

  private fun requestClipboardFeed() {
    synchronized(lock) {
      cancelClipboardFeed()
      if (emulator.connectionState == EmulatorController.ConnectionState.CONNECTED) {
        clipboardReceiver =
          coroutineScope.launch {
            while (true) {
              try {
                emulator.streamClipboard().collect { message ->
                  logger.debug { "ClipboardReceiver.onNext: \"${message.text}\"" }
                  if (message.text.isNotEmpty()) {
                    onDeviceClipboardChanged(message.text)
                  }
                }
              } catch (_: EmulatorController.RetryException) {
                continue
              } catch (t: Throwable) {
                t.throwIfCancellation()
              }
              break
            }
          }
      }
    }
  }
}
