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
package com.android.tools.idea.transport

import com.android.tools.datastore.DataStoreService
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import io.grpc.inprocess.InProcessChannelBuilder
import java.io.File
import java.nio.file.Paths
import java.util.HashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of [TransportService]. Use [TransportService.getInstance] in production code to instantiate this class.
 */
class TransportServiceImpl @VisibleForTesting constructor() : TransportService {
  private val dataStoreService: DataStoreService
  private val deviceManager: TransportDeviceManager

  // Forever incrementing stream id for custom servers. Collision with device-based stream id is unlikely as they are generated based on
  // device's boot_id ^ timestamp.
  private val customStreamId = AtomicInteger()
  private val streamIdToServerMap: MutableMap<Long, EventStreamServer> = HashMap()

  override val logService = IntellijLogService()
  override val messageBus = ApplicationManager.getApplication().messageBus

  override fun dispose() {
    streamIdToServerMap.values.forEach(EventStreamServer::stop)
    streamIdToServerMap.clear()
    dataStoreService.shutdown()
  }

  override fun registerStreamServer(streamType: Common.Stream.Type, streamServer: EventStreamServer): Common.Stream {
    val stream = Common.Stream.newBuilder().setStreamId(customStreamId.incrementAndGet().toLong()).setType(streamType).build()
    val channel = InProcessChannelBuilder.forName(streamServer.serverName).usePlaintext().directExecutor().build()
    dataStoreService.connect(stream, channel)
    streamIdToServerMap[stream.streamId] = streamServer
    return stream
  }

  override fun unregisterStreamServer(streamId: Long) {
    streamIdToServerMap[streamId]?.let {
      it.stop()
      dataStoreService.disconnect(streamId)
      streamIdToServerMap.remove(streamId)
    }
  }

  companion object {
    private val logger = Logger.getInstance(TransportServiceImpl::class.java)
  }

  init {
    val datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator
    dataStoreService = DataStoreService(TransportService.CHANNEL_NAME, datastoreDirectory,
                                        { runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable) }, logService)
    dataStoreService.setNoPiiExceptionHandler { t -> logger.error(NoPiiException(t)) }
    deviceManager = TransportDeviceManager(dataStoreService, messageBus, this)
  }
}