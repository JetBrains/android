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
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.FOLDING_STATE_ICONS
import com.android.tools.idea.streaming.device.DeviceState.Property
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.toTitleCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Controls the device by sending control messages to it.
 */
internal class DeviceController(
  disposableParent: Disposable,
  private val controlChannel: SuspendingSocketChannel
) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(javaClass.simpleName, 1)
  private val outputStream = Base128OutputStream(newOutputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE))
  private val suspendingInputStream = newInputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE)
  private val inputStream = Base128InputStream(suspendingInputStream)
  private val receiverScope: CoroutineScope
  private val deviceClipboardListeners: MutableList<DeviceClipboardListener> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val deviceStateListeners: MutableList<DeviceStateListener> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val displayListeners:  MutableList<DisplayListener> = ContainerUtil.createLockFreeCopyOnWriteList()
  @Volatile
  internal var supportedFoldingStates: List<FoldingState> = emptyList()
    private set
  @Volatile
  internal var currentFoldingState: FoldingState? = null
    private set
  private val responseCallbacks = ResponseCallbackMap()
  private val requestIdCounter = AtomicInteger()
  private val requestIdGenerator: () -> Int
    get() = { requestIdCounter.getAndIncrement() }

  init {
    Disposer.register(disposableParent, this)
    receiverScope = AndroidCoroutineScope(this)
    startReceivingMessages()
  }

  /**
   * Sends a control message to the device. The control message is not expected to trigger a response.
   */
  fun sendControlMessage(message: ControlMessage) {
    try {
      executor.submit {
        send(message)
      }
    }
    catch (ignore: RejectedExecutionException) {
      // Executor has been shut down by the dispose method.
    }
  }

  @Throws(StatusRuntimeException::class, TimeoutException::class)
  suspend fun getDisplayConfigurations(): List<DisplayDescriptor> {
    val request = DisplayConfigurationRequest(requestIdGenerator)
    return (sendRequest(request, RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS) as? DisplayConfigurationResponse)?.displays ?:
           throw RuntimeException("Unexpected response")
  }

  @Throws(StatusRuntimeException::class, TimeoutException::class)
  suspend fun getUiSettings(): UiSettingsResponse {
    val request = UiSettingsRequest(requestIdGenerator)
    return sendRequest(request, RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS) as? UiSettingsResponse
           ?: throw RuntimeException("Unexpected response")
  }

  /**
   * Sends a request message to the device and returns the received response.
   * The [request] has to implement the [CorrelatedMessage] interface.
   */
  @Throws(StatusRuntimeException::class, TimeoutException::class)
  private suspend fun sendRequest(request: ControlMessage, timeout: Long, unit: TimeUnit): ControlMessage {
    require(request is CorrelatedMessage)
    try {
      return withTimeout(unit.toMillis(timeout)) {
        suspendCancellableCoroutine { continuation ->
          responseCallbacks.put(request.requestId, continuation)
          try {
            executor.submit {
              send(request)
            }
          }
          catch (e: RejectedExecutionException) {
            continuation.cancel() // Executor has been shut down by the dispose method.
          }
          catch (e: Throwable) {
            continuation.resumeWithException(e)
          }
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      throw TimeoutException()
    }
    finally {
      responseCallbacks.remove(request.requestId)
    }
  }

  private fun send(message:ControlMessage) {
    message.serialize(outputStream)
    outputStream.flush()
  }

  override fun dispose() {
    executor.shutdown()
    responseCallbacks.cancelAll()
    receiverScope.launch { controlChannel.close() }
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

  /** Adds a listener of that is called when device displays are added or removed. */
  internal fun addDisplayListener(listener: DisplayListener) {
    displayListeners.add(listener)
  }

  internal fun removeDisplayListener(listener: DisplayListener) {
    displayListeners.remove(listener)
  }

  private fun startReceivingMessages() {
    receiverScope.launch {
      while (true) {
        try {
          if (inputStream.available() == 0) {
            suspendingInputStream.waitForData(1)
          }
          when (val message = ControlMessage.deserialize(inputStream)) {
            is CorrelatedMessage -> onResponse(message)
            is ClipboardChangedNotification -> onDeviceClipboardChanged(message)
            is SupportedDeviceStatesNotification -> onSupportedDeviceStatesChanged(message)
            is DeviceStateNotification -> onDeviceStateChanged(message)
            is DisplayAddedNotification -> onDisplayAdded(message)
            is DisplayRemovedNotification -> onDisplayRemoved(message)
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

  private fun onResponse(response: CorrelatedMessage) {
    val continuation = responseCallbacks.remove(response.requestId) ?: return
    if (response is ErrorResponse) {
      continuation.resumeWithException(StatusRuntimeException(Status.UNKNOWN.withDescription(response.errorMessage)))
    }
    else {
      continuation.resume(response)
    }
  }

  private fun onDeviceClipboardChanged(message: ClipboardChangedNotification) {
    val text = message.text
    for (listener in deviceClipboardListeners) {
      listener.onDeviceClipboardChanged(text)
    }
  }

  private fun onSupportedDeviceStatesChanged(message: SupportedDeviceStatesNotification) {
    val foldingStates = message.deviceStates.map { FoldingState(it) }
    supportedFoldingStates = foldingStates
    setFoldingState(message.deviceStateId)

    for (listener in deviceStateListeners) {
      listener.onSupportedDeviceStatesChanged(foldingStates)
    }
  }

  private fun onDeviceStateChanged(message: DeviceStateNotification) {
    setFoldingState(message.deviceStateId)
    for (listener in deviceStateListeners) {
      listener.onDeviceStateChanged(message.deviceStateId)
    }
  }

  fun setFoldingState(stateId: Int) {
    currentFoldingState = supportedFoldingStates.find { it.id == stateId }
  }

  private fun onDisplayAdded(message: DisplayAddedNotification) {
    for (listener in displayListeners) {
      listener.onDisplayAdded(message.displayId)
    }
  }

  private fun onDisplayRemoved(message: DisplayRemovedNotification) {
    for (listener in displayListeners) {
      listener.onDisplayRemoved(message.displayId)
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

  internal interface DisplayListener {
    @AnyThread
    fun onDisplayAdded(displayId: Int)

    @AnyThread
    fun onDisplayRemoved(displayId: Int)
  }

  private class ResponseCallbackMap {
    private val responseCallbacks = Int2ObjectOpenHashMap<CancellableContinuation<ControlMessage>>()

    @Synchronized
    fun put(requestId: Int, callback: CancellableContinuation<ControlMessage>) {
      if (responseCallbacks.put(requestId, callback) != null) {
        logger<DeviceController>().error("Duplicate request ID: $requestId")
      }
    }

    @Synchronized
    fun remove(requestId: Int): CancellableContinuation<ControlMessage>? {
      return responseCallbacks.remove(requestId)
    }

    @Synchronized
    fun cancelAll() {
      for (continuation in responseCallbacks.values) {
        continuation.cancel()
      }
    }
  }
}

internal data class FoldingState(
  val id: Int,
  val name: String,
  val systemProperties: Set<Property> = emptySet(),
  val physicalProperties: Set<Property> = emptySet(),
) {

  val icon: Icon? = FOLDING_STATE_ICONS[name]

  constructor(deviceState: DeviceState) :
    this(deviceState.id, deviceState.adjustedName, deviceState.adjustedSystemProperties, deviceState.physicalProperties)
}

private val DeviceState.adjustedName: String
  get() {
    var adjustedName = name.replaceSuffix("_STATE", "_MODE")
    adjustedName = when (adjustedName) {
      "CLOSE" -> "CLOSED"
      "OPENED" -> "OPEN"
      "HALF_CLOSED" -> "HALF_OPEN"
      "HALF_FOLDED" -> "HALF_OPEN"
      "HALF_OPENED" -> "HALF_OPEN"
      "CONCURRENT_INNER_DEFAULT", "DUAL" -> "DUAL_DISPLAY_MODE"
      "REAR_DUAL" -> "REAR_DUAL_MODE"
      else -> adjustedName
    }
    if (adjustedName.startsWith("HALF_")) {
      adjustedName = "HALF-" + adjustedName.substring("HALF_".length)
    }
    return toTitleCase(adjustedName.replace('_', ' ').lowercase())
  }

private val DeviceState.adjustedSystemProperties: Set<Property>
  // For some unclear reason the video encoder connected to the second display on Samsung Fold5 doesn't
  // produce any frames in REAR_DUAL mode. As a workaround, make that mode harder to switch to.
  get() = if (name == "REAR_DUAL") systemProperties + Property.PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP else systemProperties

private fun String.replaceSuffix(old: String, new: String): String {
  return if (endsWith(old)) substring(0, length - old.length) + new else this
}

private const val CONTROL_MSG_BUFFER_SIZE = 4096

private const val RESPONSE_TIMEOUT_SEC = 10L