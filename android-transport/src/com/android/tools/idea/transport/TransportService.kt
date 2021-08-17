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
package com.android.tools.idea.transport

import com.android.tools.datastore.LogService
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.util.messages.MessageBus

/**
 * An application-level service for establishing a connection to a device, which can then be used to retrieve Android system and app data.
 * The service is application-level because devices/processes are accessible through multiple projects, and we want the pipeline to work
 * across project where users can use different client features in multiple studio instances.
 */
interface TransportService : Disposable {
  val logService: LogService
  val messageBus: MessageBus

  /**
   * Registers an [EventStreamServer] for the given [streamType] to push events and bytes via the transport pipeline.
   *
   * @return The [Common.Stream] instance that was created for the server.
   */
  fun registerStreamServer(streamType: Common.Stream.Type, streamServer: EventStreamServer): Common.Stream
  fun unregisterStreamServer(streamId: Long)

  companion object {
    @JvmStatic
    fun getInstance() = service<TransportService>()

    const val CHANNEL_NAME = "DataStoreService"
  }
}