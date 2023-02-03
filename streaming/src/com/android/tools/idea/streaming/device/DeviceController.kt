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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Controls the device by sending control messages to it.
 */
class DeviceController(
  disposableParent: Disposable,
  controlChannel: SuspendingSocketChannel
) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(javaClass.simpleName, 1)
  private val outputStream = Base128OutputStream(newOutputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE))
  private val suspendingInputStream = newInputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE)
  private val inputStream = Base128InputStream(suspendingInputStream)
  private val receiverScope: CoroutineScope
  private val deviceClipboardListeners: MutableList<DeviceClipboardListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  init {
    Disposer.register(disposableParent, this)
    receiverScope = AndroidCoroutineScope(this)
    startReceivingMessages()
  }

  fun sendControlMessage(message: ControlMessage) {
    if (!executor.isShutdown) {
      executor.submit {
        send(message)
      }
    }
  }

  private fun send(message:ControlMessage) {
    message.serialize(outputStream)
    outputStream.flush()
  }

  fun sendKeyStroke(androidKeyStroke: AndroidKeyStroke) {
    pressMetaKeys(androidKeyStroke.metaState)
    sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_DOWN_AND_UP, androidKeyStroke.keyCode, androidKeyStroke.metaState))
    releaseMetaKeys(androidKeyStroke.metaState)
  }

  // Simulates pressing of meta keys corresponding to the given [metaState].
  private fun pressMetaKeys(metaState: Int) {
    if (metaState != 0) {
      var currentMetaState = 0
      for ((key, state) in ANDROID_META_KEYS) {
        if ((metaState and state) != 0) {
          currentMetaState = currentMetaState or state
          sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_DOWN, key, currentMetaState))
          if (currentMetaState == metaState) {
            break
          }
        }
      }
    }
  }

  // Simulates releasing of meta keys corresponding to the given [metaState].
  private fun releaseMetaKeys(metaState: Int) {
    if (metaState != 0) {
      // Simulate releasing of meta keys.
      var currentMetaState = metaState
      for ((key, state) in ANDROID_META_KEYS.asReversed()) {
        if ((currentMetaState and state) != 0) {
          currentMetaState = currentMetaState and state.inv()
          sendControlMessage(KeyEventMessage(AndroidKeyEventActionType.ACTION_UP, key, currentMetaState))
          if (currentMetaState == 0) {
            break
          }
        }
      }
    }
  }

  override fun dispose() {
    executor.shutdown()
    try {
      executor.awaitTermination(2, TimeUnit.SECONDS)
    }
    finally {
      deviceClipboardListeners.clear()
    }
  }

  fun addDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.add(listener)
  }

  fun removeDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.remove(listener)
  }

  private fun startReceivingMessages() {
    receiverScope.launch {
      while (true) {
        try {
          suspendingInputStream.waitForData(1)
          when (val message = ControlMessage.deserialize(inputStream)) {
            is ClipboardChangedNotification -> onDeviceClipboardChanged(message)
            else -> thisLogger().error("Unexpected type of a received message: ${message.type}")
          }
        }
        catch (_: EOFException) {
          break
        }
        catch (e: IOException) {
          if (e.message?.startsWith("Connection reset") == true) {
            break
          }
          throw e
        }
      }
    }
  }

  private fun onDeviceClipboardChanged(message: ClipboardChangedNotification) {
    val text = message.text
    for (listener in deviceClipboardListeners) {
      listener.onDeviceClipboardChanged(text)
    }
  }

  interface DeviceClipboardListener {
    @AnyThread
    fun onDeviceClipboardChanged(text: String)
  }
}

private const val CONTROL_MSG_BUFFER_SIZE = 4096

/** Android meta keys and their corresponding meta states. */
private val ANDROID_META_KEYS = listOf(
  AndroidKeyStroke(AKEYCODE_ALT_LEFT, AMETA_ALT_ON),
  AndroidKeyStroke(AKEYCODE_SHIFT_LEFT, AMETA_SHIFT_ON),
  AndroidKeyStroke(AKEYCODE_CTRL_LEFT, AMETA_CTRL_ON),
  AndroidKeyStroke(AKEYCODE_META_LEFT, AMETA_META_ON),
)
