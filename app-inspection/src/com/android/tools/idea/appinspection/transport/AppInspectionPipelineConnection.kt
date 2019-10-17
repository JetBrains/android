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
package com.android.tools.idea.appinspection.transport

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.android.tools.profiler.proto.Common.Process
import com.android.tools.profiler.proto.Common.Stream
import com.google.common.util.concurrent.ListenableFuture
import java.nio.file.Path
import java.util.concurrent.ExecutorService

/**
 * Represents the connection interface between studio and the app-inspection pipeline per process.
 */
interface AppInspectionPipelineConnection {
  /**
   * Creates an inspector in the connected process.
   *
   * @param [inspectorJar] is the path to a jar on the host filesystem that contains ".dex" code of an inspector and
   * configuration file: META-INF/services/androidx.inspection.InspectorFactory, where a factory for an inspector should be registered.
   * [inspectorJar] will be injected into the app's process and an inspector will be instantiated with the registered factory.
   * @param [inspectorId] an id of an inspector to launch; the factory injected into the app with [inspectorJar] must have the same id as
   * a value passed into this function.
   * @param [creator] a factory lambda to instantiate a [AppInspectorClient] once inspector is successfully created on the device side.
   */
  @WorkerThread
  fun <T : AppInspectorClient> launchInspector(
    inspectorId: String,
    inspectorJar: Path,
    @WorkerThread creator: (AppInspectorClient.CommandMessenger) -> T
  ): ListenableFuture<T>


  companion object {
    /**
     * Creates a [AppInspectionPipelineConnection] to the given [process] on the given device ([stream])
     */
    @WorkerThread
    fun attach(
      stream: Stream,
      process: Process,
      client: TransportClient = TransportClient(TransportService.getInstance().channelName),
      executorService: ExecutorService
    ): ListenableFuture<AppInspectionPipelineConnection> = attachAppInspectionPipelineConnection(client, stream, process, executorService)
  }
}