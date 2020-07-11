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
package com.android.tools.idea.transport.faketransport

import com.android.tools.datastore.FakeLogService
import com.android.tools.idea.transport.EventStreamServer
import com.android.tools.idea.transport.TransportService
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Test implementation of [TransportService].
 * <p>
 *   Usage example:
 * <pre>
 *   @get:Rule val applicationRule = ApplicationRule()
 *
 *   @Before
 *   fun setUp() {
 *     ApplicationManager.getApplication().registerServiceInstance(Transport::java.class, TransportServiceTestImpl())
 *   }
 * </pre>
 */
class TransportServiceTestImpl(private val transportRpcService: FakeTransportService) : TransportService {
  private val customStreamId = AtomicLong()
  private val streamServerMap = mutableMapOf<Long, EventStreamServer>()

  override val logService = FakeLogService()
  override val messageBus = ApplicationManager.getApplication().messageBus

  override fun registerStreamServer(streamType: Common.Stream.Type, streamServer: EventStreamServer): Common.Stream {
    val stream = Common.Stream.newBuilder().setStreamId(customStreamId.incrementAndGet()).setType(streamType).build()
    streamServerMap[stream.streamId] = streamServer
    transportRpcService.connectToStreamServer(stream, streamServer)
    return stream
  }

  override fun unregisterStreamServer(streamId: Long) {
    transportRpcService.disconnectFromStreamServer(streamId)
    streamServerMap[streamId]?.let {
      it.stop()
      streamServerMap.remove(streamId)
    }
  }

  override fun dispose() {
    streamServerMap.keys.forEach(this::unregisterStreamServer)
  }
}