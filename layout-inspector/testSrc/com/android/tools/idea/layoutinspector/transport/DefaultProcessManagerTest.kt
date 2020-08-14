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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.layoutinspector.DEFAULT_DEVICE
import com.android.tools.idea.layoutinspector.DEFAULT_PROCESS
import com.android.tools.idea.layoutinspector.util.ProcessManagerAsserts
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.testFramework.DisposableRule
import io.grpc.ManagedChannel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val OTHER_PROCESS = Common.Process.newBuilder().apply {
  name = "myOtherProcess"
  pid = 23456
  deviceId = DEFAULT_DEVICE.deviceId
  state = Common.Process.State.ALIVE
}.build()!!

class DefaultProcessManagerTest {
  private var timer: FakeTimer? = FakeTimer()
  private var transportService: FakeTransportService? = FakeTransportService(timer!!)
  private var client: TransportClient? = null
  private var streamManager: TransportStreamManager? = null
  private var processManager: DefaultProcessManager? = null

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val grpcServer: FakeGrpcServer? =
    FakeGrpcServer.createFakeGrpcServer("LayoutInspectorTestChannel", transportService, transportService)

  @Before
  fun before() {
    client = TransportClient(grpcServer!!.name)
    streamManager = TransportStreamManager.createManager(client!!.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    processManager = DefaultProcessManager(client!!.transportStub, executor, streamManager!!, disposableRule.disposable)
  }

  @After
  fun after() {
    closeChannel()
    streamManager?.let { TransportStreamManager.unregisterManager(it) }
    processManager = null
    streamManager = null
    client = null
    transportService = null
    timer = null
  }

  private fun closeChannel() {
    val channel = client?.transportStub?.channel as? ManagedChannel ?: return
    channel.shutdown()
    channel.awaitTermination(1, TimeUnit.SECONDS)
  }

  @Test
  fun addDevice() {
    transportService!!.addDevice(DEFAULT_DEVICE)

    val waiter = ProcessManagerAsserts(processManager!!)
    waiter.assertDeviceWithProcesses(DEFAULT_DEVICE.serial)
  }

  @Test
  fun addProcess() {
    startProcesses()
  }

  @Test
  fun removeProcess() {
    startProcesses()

    val stream = processManager!!.getStreams().single()
    val process = processManager!!.getProcesses(stream).first { it.pid == 12345 }

    // Despite the confusing name, this triggers a process end event.
    val offlineProcess = process.toBuilder().setState(Common.Process.State.DEAD).build()
    transportService?.addProcess(stream.device, offlineProcess)

    val waiter = ProcessManagerAsserts(processManager!!)
    waiter.assertDeviceWithProcesses(DEFAULT_DEVICE.serial, OTHER_PROCESS.pid)
  }

  @Test
  fun stopDevice() {
    startProcesses()

    val stream = processManager!!.getStreams().single()
    val update = stream.device.toBuilder().setState(Common.Device.State.DISCONNECTED).build()
    transportService!!.addDevice(update)

    val waiter = ProcessManagerAsserts(processManager!!)
    waiter.assertNoDevices()
  }

  private fun startProcesses() {
    startProcesses(DEFAULT_DEVICE, DEFAULT_PROCESS, OTHER_PROCESS)
    val waiter = ProcessManagerAsserts(processManager!!)
    waiter.assertDeviceWithProcesses(DEFAULT_DEVICE.serial, DEFAULT_PROCESS.pid, OTHER_PROCESS.pid)
    timer!!.currentTimeNs += 1
  }

  private fun startProcesses(device: Common.Device, vararg processes: Common.Process) {
    transportService!!.addDevice(device)
    processes.forEach { transportService!!.addProcess(device, it) }
  }
}
