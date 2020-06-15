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
package com.android.tools.idea.appinspection.ide

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.appinspection.api.AppInspectionDiscovery
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.api.AppInspectorJar
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.android.tools.idea.transport.DeployableFile
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.TransportServiceProxy
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.components.ServiceManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit


typealias AdbDeviceFinder = (Common.Device) -> IDevice?

/**
 * This service holds a reference to [AppInspectionDiscoveryManager]. It will establish new connections when they are discovered.
 */
internal class AppInspectionHostService {
  private val channel = object : AppInspectionDiscoveryHost.Channel {
    override val name = TransportService.CHANNEL_NAME
  }

  private val client = TransportClient(channel.name)
  private val poller = TransportEventPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
  val discoveryManager = AppInspectionDiscoveryManager(
    client, AppExecutorUtil.getAppScheduledExecutorService(), poller,
    { device -> AndroidDebugBridge.getBridge()?.devices?.first { TransportServiceProxy.transportDeviceFromIDevice(it) == device } })

  companion object {
    val instance: AppInspectionHostService
      get() = ServiceManager.getService(AppInspectionHostService::class.java)
  }
}

/**
 * Keeps track of streams and processes as they start and stop.
 *
 * When a new process is started, an attempt is made to connect to the process and discover it via [discoveryHost].
 */
@VisibleForTesting
internal class AppInspectionDiscoveryManager(
  client: TransportClient,
  private val executor: Executor,
  private val poller: TransportEventPoller,
  private val adbDeviceFinder: AdbDeviceFinder,
  private val messageBus: MessageBus = TransportService.getInstance().messageBus
) {
  @VisibleForTesting
  internal val streamIdMap = ConcurrentHashMap<Long, Common.Stream>()

  @VisibleForTesting
  internal val processIdMap = ConcurrentHashMap<Long, Common.Process>()

  val discoveryHost = AppInspectionDiscoveryHost(AppExecutorUtil.getAppScheduledExecutorService(), client, poller)

  init {
    registerListenersForDiscovery(discoveryHost)
  }

  private fun registerListenersForDiscovery(discoveryHost: AppInspectionDiscoveryHost) {
    // Create listener for STREAM connected
    val streamConnectedListener = TransportEventListener(Common.Event.Kind.STREAM, executor, { it.stream.hasStreamConnected() }) {
      val stream = it.stream.streamConnected.stream
      streamIdMap[stream.streamId] = stream
      false
    }
    poller.registerListener(streamConnectedListener)

    // Create listener for STREAM disconnected
    val streamDisconnectedListener = TransportEventListener(Common.Event.Kind.STREAM, executor, { !it.stream.hasStreamConnected() }) {
      // Group ID is the stream ID
      streamIdMap.remove(it.groupId)
      false
    }
    poller.registerListener(streamDisconnectedListener)

    // Create listener for PROCESS started
    val processStartedListener = TransportEventListener(Common.Event.Kind.PROCESS, executor, { it.process.hasProcessStarted() }) {
      // Group ID here is the process ID
      val process = it.process.processStarted.process
      processIdMap[it.groupId] = process

      val stream = streamIdMap[process.deviceId]!!
      val device = adbDeviceFinder(stream.device) ?: return@TransportEventListener false
      discoveryHost.connect(
        object : AppInspectionJarCopier {
          private val delegate = TransportFileManager(device, messageBus)
          override fun copyFileToDevice(jar: AppInspectorJar): List<String> = delegate.copyFileToDevice(jar.toDeployableFile())
        },
        ProcessDescriptor(stream, process)
      )
      false
    }
    poller.registerListener(processStartedListener)

    // Create listener for PROCESS stopped
    val processEndedListener = TransportEventListener(Common.Event.Kind.PROCESS, executor, { !it.process.hasProcessStarted() }) {
      // Group ID here is the process ID
      processIdMap.remove(it.groupId)
      false
    }
    poller.registerListener(processEndedListener)
  }

  private fun AppInspectorJar.toDeployableFile() = DeployableFile.Builder(name).apply {
    releaseDirectory?.let { this.setReleaseDir(it) }
    developmentDirectory?.let { this.setDevDir(it) }
  }.build()
}

// "class" is used simply as a namespace, an object by itself must be stateless. It is purpose is to provide public access to subset
// of AppInspectionHostService API: clients can add a listener to be notified about new connections, but can't establish new one.
object AppInspectionClientsService {
  val discovery: AppInspectionDiscovery
    get() = AppInspectionHostService.instance.discoveryManager.discoveryHost.discovery
}

