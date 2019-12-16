/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.api

import com.android.annotations.concurrency.WorkerThread
import com.google.common.util.concurrent.ListenableFuture

/**
 * Base class for implementing a client of known app-inspector.
 *
 * Implementations can use [messenger] to send commands to an inspector and listens for events from it via [eventListener].
 */
abstract class AppInspectorClient(
  val messenger: CommandMessenger
) {
  /** Interface for defining a connection that sends basic commands and receives callbacks between studio and inspectors. */
  interface CommandMessenger {
    /**
     * Disposes the inspector and returns a future of the service response.
     *
     * Upon the response this inspector will be considered disposed, no matter if the call succeeded or failed. (In case of the error
     * response the inspector is considered unusable). All pending commands on the moment of disposal are resolved with an exception.
     */
    @WorkerThread
    fun disposeInspector(): ListenableFuture<Unit>

    /**
     * Sends a raw command using the provided [rawData]. Returns a future of a raw response.
     */
    @WorkerThread
    fun sendRawCommand(rawData: ByteArray): ListenableFuture<ByteArray>
  }

  /**
   * Interface for all types of events from an inspector.
   */
  interface EventListener {
    /**
     * Callback for raw events sent by inspector.
     */
    @WorkerThread
    fun onRawEvent(eventData: ByteArray) {}

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

  abstract val eventListener: EventListener
}