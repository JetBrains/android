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
import com.android.tools.idea.streaming.core.FOLDING_STATE_ICONS
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.toTitleCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.Icon

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
  private val deviceStateListeners: MutableList<DeviceStateListener> = ContainerUtil.createLockFreeCopyOnWriteList()
  @Volatile
  internal var supportedFoldingStates: List<FoldingState> = emptyList()
    private set
  @Volatile
  internal var currentFoldingState: FoldingState? = null
    private set

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

  override fun dispose() {
    executor.shutdown()
    try {
      executor.awaitTermination(2, TimeUnit.SECONDS)
    }
    finally {
      deviceClipboardListeners.clear()
    }
  }

  internal fun addDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.add(listener)
  }

  internal fun removeDeviceClipboardListener(listener: DeviceClipboardListener) {
    deviceClipboardListeners.remove(listener)
  }

  /**
   * Adds a [DeviceStateListener]. The added listener immediately receives a call with
   * the current supported device states.
   */
  internal fun addDeviceStateListener(listener: DeviceStateListener) {
    deviceStateListeners.add(listener)
    listener.onSupportedDeviceStatesChanged(supportedFoldingStates)
  }

  internal fun removeDeviceStateListener(listener: DeviceStateListener) {
    deviceStateListeners.remove(listener)
  }

  private fun startReceivingMessages() {
    receiverScope.launch {
      while (true) {
        try {
          if (inputStream.available() == 0) {
            suspendingInputStream.waitForData(1)
          }
          when (val message = ControlMessage.deserialize(inputStream)) {
            is ClipboardChangedNotification -> onDeviceClipboardChanged(message)
            is SupportedDeviceStatesNotification -> onSupportedDeviceStatesChanged(message)
            is DeviceStateNotification -> onDeviceStateChanged(message)
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

  private fun onSupportedDeviceStatesChanged(message: SupportedDeviceStatesNotification) {
    val deviceStates = try {
      parseDeviceStates(message.text)
    }
    catch (e: IllegalArgumentException) {
      thisLogger().error("Unexpected supported states message:\n${message.text}")
      return
    }
    supportedFoldingStates = deviceStates
    for (listener in deviceStateListeners) {
      listener.onSupportedDeviceStatesChanged(deviceStates)
    }
  }

  /**
   * Builds a list of [FoldingState]s by parsing a string like:
   * ```
   * Supported states: [
   *   DeviceState{identifier=0, name='CLOSE', app_accessible=true},
   *   DeviceState{identifier=1, name='TENT', app_accessible=true},
   *   DeviceState{identifier=2, name='HALF_FOLDED', app_accessible=true},
   *   DeviceState{identifier=3, name='OPEN', app_accessible=true},
   * ]
   * ```
   */
  private fun parseDeviceStates(text: String): List<FoldingState> {
    val regex = Regex("DeviceState\\{identifier=(?<id>\\d+), name='(?<name>\\w+)', app_accessible=(?<accessible>true|false)}")
    return regex.findAll(text).map {
      val groups = it.groups
      val id = groups["id"]?.value?.toInt() ?: throw IllegalArgumentException()
      val name = groups["name"]?.value ?: throw IllegalArgumentException()
      val accessible = groups["accessible"]?.value == "true"
      FoldingState(id, deviceStateNameToFoldingStateName(name), accessible)
    }.toList()
  }

  private fun deviceStateNameToFoldingStateName(name: String): String {
    var correctedName = name.removeSuffix("_STATE")
    correctedName = when (correctedName) {
      "CLOSE" -> "CLOSED"
      "OPENED" -> "OPEN"
      "HALF_CLOSED" -> "HALF_OPEN"
      "HALF_FOLDED" -> "HALF_OPEN"
      "HALF_OPENED" -> "HALF_OPEN"
      "CONCURRENT_INNER_DEFAULT" -> "BOTH_DISPLAYS"
      else -> correctedName
    }
    if (correctedName.startsWith("HALF_")) {
      correctedName = "HALF-" + correctedName.substring("HALF_".length)
    }
    return toTitleCase(correctedName.replace('_', ' ').lowercase())
  }

  private fun onDeviceStateChanged(message: DeviceStateNotification) {
    currentFoldingState = supportedFoldingStates.find { it.id == message.deviceState }
    for (listener in deviceStateListeners) {
      listener.onDeviceStateChanged(message.deviceState)
    }
  }

  internal interface DeviceClipboardListener {
    @AnyThread
    fun onDeviceClipboardChanged(text: String)
  }

  internal interface DeviceStateListener {
    @AnyThread
    fun onSupportedDeviceStatesChanged(deviceStates: List<FoldingState>)

    @AnyThread
    fun onDeviceStateChanged(deviceState: Int)
  }
}

internal data class FoldingState(val id: Int, val name: String, val appAccessible: Boolean) {
  val icon: Icon? = FOLDING_STATE_ICONS[name]
}

private const val CONTROL_MSG_BUFFER_SIZE = 4096
