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

import com.android.tools.idea.appinspection.internal.AppInspectionAttacher
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileCopier
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Function

typealias TargetListener = (AppInspectionTarget) -> Unit

/**
 * A class that hosts an [AppInspectionDiscovery] instance and exposes a method to initialize its connection.
 *
 * Ideally, a central host service in the calling code will create and connect this discovery and then share just
 * the discovery with interested clients.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscoveryHost(executor: ScheduledExecutorService, channel: Channel) {
  /**
   * This class represents a channel between some host (which should implement this class) and a target Android device.
   */
  interface Channel {
    val name: String
  }

  val discovery = AppInspectionDiscovery(executor, channel)

  /**
   * Connects to a process on device defined by [preferredProcess]. This method returns a future of [AppInspectionPipelineConnection]. If
   * the connection is cached, the future is ready to be gotten immediately.
   */
  fun connect(
    transportFileCopier: TransportFileCopier,
    preferredProcess: AutoPreferredProcess
  ): ListenableFuture<AppInspectionTarget> = discovery.connect(transportFileCopier, preferredProcess)
}

/**
 * A class which listens for [AppInspectionTarget] instances when they become available.
 *
 * Note that even if there are multiple devices, this class is designed so that a single discovery instance could be connected
 * to and fire listeners for all of them.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscovery(
  private val executor: ScheduledExecutorService,
  transportChannel: AppInspectionDiscoveryHost.Channel
) {
  private val listeners = ConcurrentHashMap<TargetListener, Executor>()
  private val attacher = AppInspectionAttacher(executor, transportChannel)

  private val connections = ConcurrentHashMap<AutoPreferredProcess, ListenableFuture<AppInspectionTarget>>()
  private val client = TransportClient(transportChannel.name)
  private val transportPoller = TransportEventPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))

  internal fun connect(
    fileCopier: TransportFileCopier,
    preferredProcess: AutoPreferredProcess
  ): ListenableFuture<AppInspectionTarget> {
    return connections.computeIfAbsent(
      preferredProcess,
      Function { autoPreferredProcess ->
        val connectionFuture = SettableFuture.create<AppInspectionTarget>()
        attacher.attach(autoPreferredProcess) { stream, process ->
          val transport = AppInspectionTransport(client, stream, process, executor, transportPoller)
          AppInspectionTarget.attach(transport, fileCopier)
            .transform(executor) { connection ->
              listeners.forEach { it.value.execute { it.key(connection) } }
              connectionFuture.set(connection)
            }
        }
        connectionFuture
      })
  }

  fun addTargetListener(executor: Executor, listener: TargetListener): TargetListener {
    listeners[listener] = executor
    return listener
  }
}
