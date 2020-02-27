/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.Slow
import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.EmulatorStatus
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.SensorValue
import com.android.tools.idea.protobuf.Empty
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls a running Emulator.
 */
class EmulatorController(val emulatorId: EmulatorId, parentDisposable: Disposable) : Disposable {
  private var channel: ManagedChannel? = null
  private var emulatorController: EmulatorControllerGrpc.EmulatorControllerStub? = null
  @Volatile private var emulatorConfigInternal: EmulatorConfiguration? = null
  private var stateInternal = AtomicReference(State.NOT_INITIALIZED)
  private val connectionStateListeners: ConcurrentList<ConnectionStateListener> = ContainerUtil.createConcurrentList()

  var emulatorConfig: EmulatorConfiguration
    get() {
      return emulatorConfigInternal ?: throw IllegalStateException("Not yet connected to the Emulator")
    }
    private set(value) {
      emulatorConfigInternal = value
    }

  var state: State
    get() {
      return stateInternal.get()
    }
    private set(value) {
      if (stateInternal.getAndSet(value) != value) {
        for (listener in connectionStateListeners) {
          listener.stateChanged(this, value)
        }
      }
    }

  @AnyThread
  fun addConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.add(listener)
  }

  @AnyThread
  fun removeConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.remove(listener)
  }

  init {
    Disposer.register(parentDisposable, this)
  }

  /**
   * Establishes a connection to the Emulator. The process of establishing connection is asynchronous,
   * but the synchronous part of this method also takes considerable time.
   */
  @Slow
  fun connect() {
    state = State.CONNECTING
    channel = NettyChannelBuilder
      .forAddress("localhost", emulatorId.grpcPort)
      .usePlaintext(true) // TODO: Add proper authentication.
      .maxMessageSize(16 * 1024 * 1024)
      .build()
    emulatorController = EmulatorControllerGrpc.newStub(channel)
    fetchConfiguration()
  }

  /**
   * Sends a [KeyboardEvent] to the Emulator.
   */
  fun sendKey(keyboardEvent: KeyboardEvent, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    emulatorController?.sendKey(keyboardEvent, DelegatingStreamObserver(streamObserver)) ?: throw IllegalStateException()
  }

  /**
   * Retrieves a sensor value.
   */
  fun getSensor(sensorType: SensorValue.SensorType, streamObserver: StreamObserver<SensorValue>) {
    val sensorValue = SensorValue.newBuilder().setTarget(sensorType).build()
    emulatorController?.getSensor(sensorValue, DelegatingStreamObserver(streamObserver)) ?: throw IllegalStateException()
  }

  /**
   * Sets a sensor value.
   */
  fun setSensor(sensorValue: SensorValue, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    emulatorController?.setSensor(sensorValue, DelegatingStreamObserver(streamObserver)) ?: throw IllegalStateException()
  }

  private fun fetchConfiguration() {
    emulatorController?.getStatus(Empty.getDefaultInstance(), object : DelegatingStreamObserver<EmulatorStatus>(null) {
      override fun onNext(response: EmulatorStatus) {
        val config = EmulatorConfiguration.fromHardwareConfig(response.hardwareConfig!!)
        if (config == null) {
          LOG.warn("Incomplete hardware configuration")
          state = State.DISCONNECTED
        }
        else {
          emulatorConfig = config
          state = State.CONNECTED
        }
      }
    })
  }

  override fun dispose() {
    channel?.shutdownNow()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EmulatorController

    return emulatorId == other.emulatorId
  }

  override fun hashCode(): Int {
    return emulatorConfigInternal?.hashCode() ?: 0
  }

  /**
   * The state of the [EmulatorController].
   */
  enum class State {
    NOT_INITIALIZED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
  }

  private open inner class DelegatingStreamObserver<T>(val delegate: StreamObserver<in T>?) : StreamObserver<T> {
    override fun onNext(response: T) {
      delegate?.onNext(response)
    }

    override fun onError(t: Throwable) {
      state = State.DISCONNECTED
      delegate?.onError(t)
    }

    override fun onCompleted() {
      delegate?.onCompleted()
    }
  }

  /**
   * Defines interface for an object that receives notifications when the state of the Emulator
   * connection changes.
   */
  interface ConnectionStateListener {
    /**
     * Called when the state of the Emulator connection changes.
     */
    @AnyThread
    fun stateChanged(emulator: EmulatorController, state: State)
  }

  companion object {
    @JvmStatic
    private val LOG = Logger.getInstance(EmulatorController::class.java)
    @JvmStatic
    private val DUMMY_OBSERVER = DummyStreamObserver<Any>()
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> getDummyObserver(): StreamObserver<T> {
      return DUMMY_OBSERVER as StreamObserver<T>
    }
  }
}
