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
package com.android.tools.idea.appinspection.inspector.api

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.WorkerThread
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * Base class for implementing a client of known app-inspector.
 *
 * Implementations can use [messenger] to send commands to an inspector and listens for events from it via [rawEventListener].
 */
abstract class AppInspectorClient(
  val messenger: CommandMessenger
) {
  /** Interface for defining a connection that sends basic commands and receives callbacks between studio and inspectors. */
  interface CommandMessenger {
    /**
     * Disposes the inspector and returns a future of the service response.
     *
     * Upon response this inspector will be considered disposed, no matter if the call succeeded or failed (completed exceptionally with
     * [AppInspectionConnectionException]). The inspector will be considered unusable in either case. All pending commands at the moment of
     * disposal are resolved exceptionally.
     */
    @WorkerThread
    fun disposeInspector(): ListenableFuture<Unit>

    /**
     * Sends a raw command using the provided [rawData]. Returns a future of a raw response.
     *
     * The result future can complete exceptionally with an [AppInspectionConnectionException] when App Inspection framework encounters an
     * issue with the underlying connection to the process. Clients must be able to handle it gracefully.
     * An example would be the connection ended because the app crashed.
     */
    @WorkerThread
    fun sendRawCommand(rawData: ByteArray): ListenableFuture<ByteArray>
  }

  /**
   * Interface for raw events from an inspector.
   */
  interface RawEventListener {
    /**
     * Callback for raw events sent by inspector.
     */
    @WorkerThread
    fun onRawEvent(eventData: ByteArray) {}
  }

  /**
   * Interface for service related events from an inspector.
   */
  interface ServiceEventListener {
    /**
     * Callback for when inspector crashes.
     *
     * It is needed only for error handling. Resources clean up should be done as
     * usual in [onDispose], that will be triggered right after this call.
     */
    @WorkerThread
    fun onCrashEvent(message: String) {}

    /**
     * Callback when this inspector was disposed to free any held resources and notify any interested parties.
     *
     * After this call you shouldn't try to send any new commands via [CommandMessenger] to this inspector
     * and all pending commands are resolved with an exception.
     *
     * It can occur for various reasons: developer explicitly asked requested
     * via [CommandMessenger.disposeInspector], app's process was terminated or inspector crashed on the device.
     */
    @WorkerThread
    fun onDispose() {}
  }

  /**
   * Class that allows for the triggering of notifications on all current [ServiceEventListener] associated with this client.
   *
   * It is exposed publicly but should only be used by internal AppInspection API.
   */
  @AnyThread
  class ServiceEventNotifier {
    private val lock = Any()

    @GuardedBy("lock")
    private val listeners = mutableMapOf<ServiceEventListener, Executor>()

    @GuardedBy("lock")
    private var isCrashed = false

    @GuardedBy("lock")
    private lateinit var crashMessage: String

    @GuardedBy("lock")
    private var isDisposed = false

    internal fun addListener(listener: ServiceEventListener, executor: Executor) {
      synchronized(lock) {
        // A client is disposed soon after it crashes. There is a very small window in which it is in crashed state but not disposed, so we
        // still want to add listener if that's the case.
        if (isCrashed) {
          executor.execute { listener.onCrashEvent(crashMessage) }
        }
        if (isDisposed) {
          executor.execute { listener.onDispose() }
        } else {
          listeners[listener] = executor
        }
      }
    }

    /**
     * Only to be used by AppInspection internal modules.
     */
    fun notifyCrash(message: String) {
      synchronized(lock) {
        isCrashed = true
        crashMessage = message
        listeners.forEach { (listener, executor) -> executor.execute { listener.onCrashEvent(message) } }
      }
    }

    /**
     * Only to be used by AppInspection internal modules.
     */
    fun notifyDispose() {
      synchronized(lock) {
        isDisposed = true
        listeners.forEach { (listener, executor) -> executor.execute { listener.onDispose() } }
        listeners.clear()
      }
    }
  }

  abstract val rawEventListener: RawEventListener

  val serviceEventNotifier = ServiceEventNotifier()

  /**
   * Adds a [ServiceEventListener] to listen to dispose and crash events of this client. Callbacks are executed on [executor].
   */
  fun addServiceEventListener(listener: ServiceEventListener, executor: Executor) = serviceEventNotifier.addListener(listener, executor)
}