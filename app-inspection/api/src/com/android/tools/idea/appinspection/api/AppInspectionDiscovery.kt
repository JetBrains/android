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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.google.common.util.concurrent.ListenableFuture
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
class AppInspectionDiscoveryHost(
  executor: ScheduledExecutorService,
  client: TransportClient,
  poller: TransportEventPoller = TransportEventPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
) {
  val discovery = AppInspectionDiscovery(executor, client, poller)

  /**
   * Connects to a process on device defined by [processDescriptor]. This method returns a future of [AppInspectionTarget]. If the
   * connection is cached, the future is ready to be gotten immediately.
   */
  fun connect(
    jarCopier: AppInspectionJarCopier,
    processDescriptor: ProcessDescriptor
  ): ListenableFuture<AppInspectionTarget> =
    discovery.connect(jarCopier, processDescriptor)

  /**
   * Shuts down discovery service.
   */
  fun shutDown() {
    discovery.shutDown()
  }
}

/**
 * A class which listens for [AppInspectionTarget] instances when they become available.
 *
 * Note that even if there are multiple devices, this class is designed so that a single discovery instance could be connected
 * to and fire listeners for all of them.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscovery internal constructor(
  private val executor: ScheduledExecutorService,
  private val transportClient: TransportClient,
  private val transportPoller: TransportEventPoller
) {
  @GuardedBy("this")
  private val listeners = mutableMapOf<TargetListener, Executor>()

  @GuardedBy("this")
  private val connections = mutableMapOf<ProcessDescriptor, ListenableFuture<AppInspectionTarget>>()

  @GuardedBy("this")
  private var isShutDown = false

  internal fun connect(
    jarCopier: AppInspectionJarCopier,
    processDescriptor: ProcessDescriptor
  ): ListenableFuture<AppInspectionTarget> {
    return synchronized(this) {
      if (isShutDown) {
        throw java.lang.IllegalStateException("AppInspectionDiscovery is shut down!")
      }
      connections.computeIfAbsent(
        processDescriptor,
        Function { descriptor ->
          val transport = AppInspectionTransport(transportClient, descriptor.stream, descriptor.process, executor, transportPoller)
          AppInspectionTarget.attach(transport, jarCopier)
            .transform(executor) { connection ->
              connection.addTargetTerminatedListener(executor) { connections.remove(descriptor)?.cancel(false) }
              listeners.forEach { (listener, executor) -> executor.execute { listener(connection) } }
              connection
            }
        })
    }
  }

  internal fun shutDown() {
    synchronized(this) {
      if (!isShutDown) {
        isShutDown = true
        listeners.clear()
        connections.forEach { (_, u) -> u.cancel(false) }
      }
    }
  }

  /**
   * Adds a [TargetListener] to discovery service. Listener will receive future connections when they come online.
   *
   * This has the side effect of notifying users of all existing live connections the discovery service is aware of.
   */
  fun addTargetListener(executor: Executor, listener: TargetListener): TargetListener {
    synchronized(this) {
      if (isShutDown) {
        throw IllegalStateException("AppInspection discovery is shut down!")
      }
      listeners[listener] = executor
      connections.filterValues { it.isDone }.map { it.value.get() }.forEach { executor.execute { listener(it) } }
    }
    return listener
  }
}
