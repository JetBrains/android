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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ClipData
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.protobuf.Empty
import com.intellij.ide.ClipboardSynchronizer
import java.awt.EventQueue
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Synchronizes the AVD and the host clipboards.
 */
internal class EmulatorClipboardSynchronizer(val emulator: EmulatorController) {

  @GuardedBy("lock")
  private var clipboardFeed: Cancelable? = null
  @GuardedBy("lock")
  private var clipboardReceiver: ClipboardReceiver? = null
  @GuardedBy("lock")
  private var active = false
  private val lock = Any()

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  @UiThread
  fun setDeviceClipboardAndKeepHostClipboardInSync() {
    synchronized(lock) {
      active = true
    }
    val text = getClipboardText()
    if (text.isEmpty()) {
      requestClipboardFeed()
    }
    else {
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
    val synchronizer = ClipboardSynchronizer.getInstance()
    return if (synchronizer.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      synchronizer.getData(DataFlavor.stringFlavor) as String? ?: ""
    }
    else {
      ""
    }
  }

  private inner class ClipboardReceiver : EmptyStreamObserver<ClipData>() {
    var responseCount = 0

    override fun onNext(response: ClipData) {
      if (clipboardReceiver != this) {
        return // This clipboard feed has already been cancelled.
      }

      // Skip the first response that reflects the current clipboard state.
      if (responseCount != 0 && response.text.isNotEmpty()) {
        EventQueue.invokeLater {
          val content = StringSelection(response.text)
          ClipboardSynchronizer.getInstance().setContent(content, content)
        }
      }
      responseCount++
    }
  }
}