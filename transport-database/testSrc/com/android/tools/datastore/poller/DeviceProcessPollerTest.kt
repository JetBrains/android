/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.poller

import com.android.test.testutils.TestUtils
import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.DataStorePollerTest
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE
import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.database.DeviceProcessTable
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.Server
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder
import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.AgentStatusRequest
import com.android.tools.profiler.proto.Transport.GetDevicesRequest
import com.android.tools.profiler.proto.Transport.GetDevicesResponse
import com.android.tools.profiler.proto.Transport.GetProcessesRequest
import com.android.tools.profiler.proto.Transport.GetProcessesResponse
import com.android.tools.profiler.proto.Transport.TimeRequest
import com.android.tools.profiler.proto.Transport.TimeResponse
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class DeviceProcessPollerTest : DataStorePollerTest() {

  private lateinit var myDataStore: DataStoreService
  private lateinit var myProfilerService: FakeTransportService
  private lateinit var myDatabase: DataStoreDatabase
  private lateinit var myTable: DeviceProcessTable
  private lateinit var myServer: Server
  private lateinit var myManagedChannel: ManagedChannel
  private lateinit var myServiceStub: TransportServiceGrpc.TransportServiceBlockingStub
  private lateinit var myProcessPoller: DeviceProcessPoller

  @Before
  fun setup() {
    val servicePath = TestUtils.createTempDirDeletedOnExit().toString()
    myDataStore = DataStoreService(javaClass.simpleName, servicePath, pollTicker::run, FakeLogService())
    myProfilerService = FakeTransportService()
    val namespace = DEFAULT_SHARED_NAMESPACE
    myDatabase = myDataStore.createDatabase(servicePath + namespace.myNamespace, namespace.myCharacteristic) { _ -> }
    myTable = DeviceProcessTable()
    myTable.initialize(myDatabase.connection)

    myServer = InProcessServerBuilder.forName("ProfilerDevicePollerServer").addService(myProfilerService).build()
    myServer.start()
    myManagedChannel = InProcessChannelBuilder.forName("ProfilerDevicePollerServer").build()
    myServiceStub = TransportServiceGrpc.newBlockingStub(myManagedChannel)

    myProcessPoller = DeviceProcessPoller(myTable, myServiceStub)
    // Stops the poller as that's dependent on system clock. We need to manually test the poll method.
    myProcessPoller.stop()
  }

  @After
  fun teardown() {
    myServer.shutdownNow()
    myDataStore.shutdown()
  }

  @Test
  fun testPollingAddsDeviceAndProcessesToDatabase() {
    val processRequest = GetProcessesRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId).build()
    assertThat(myTable.devices.deviceList).isEmpty()
    assertThat(myTable.getProcesses(processRequest).processList).isEmpty()

    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myProcessPoller.poll()

    assertThat(myTable.devices.deviceCount).isEqualTo(1)
    assertThat(myTable.devices.getDevice(0)).isEqualTo(DataStorePollerTest.DEVICE)

    val processResponse = myTable.getProcesses(processRequest)
    assertThat(processResponse.processList.size).isEqualTo(3)
    assertThat(processResponse.processList).containsExactlyElementsIn(PROCESS_AGENT_MAP.keys)
  }

  @Test
  fun testPollingUpdateDeadProcesses() {
    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myProcessPoller.poll()
    myProfilerService.setProcessAgentMap(null)
    myProcessPoller.poll()

    assertThat(myTable.devices.deviceCount).isEqualTo(1)
    assertThat(myTable.devices.getDevice(0)).isEqualTo(DataStorePollerTest.DEVICE)

    val processRequest = GetProcessesRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId).build()
    val processResponse = myTable.getProcesses(processRequest)
    assertThat(processResponse.processList.size).isEqualTo(3)
    val deadProcessSet = PROCESS_AGENT_MAP.keys.map { p -> p.toBuilder().setState(Common.Process.State.DEAD).build() }
    assertThat(processResponse.processList).containsExactlyElementsIn(deadProcessSet)
  }

  @Test
  fun testStopUpdateDeadProcesses() {
    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myProcessPoller.poll()
    val processRequest = GetProcessesRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId).build()
    var processResponse = myTable.getProcesses(processRequest)
    assertThat(processResponse.processList).containsExactlyElementsIn(PROCESS_AGENT_MAP.keys)

    myProcessPoller.stop()
    processResponse = myTable.getProcesses(processRequest)
    val deadProcessSet = PROCESS_AGENT_MAP.keys.map { p -> p.toBuilder().setState(Common.Process.State.DEAD).build() }
    assertThat(processResponse.processList).containsExactlyElementsIn(deadProcessSet)

    // Validate agent status of dead processes.
    val newAgentStatusMap = PROCESS_AGENT_MAP.mapValues {
      when (it.value.status) {
        Common.AgentData.Status.UNSPECIFIED -> Common.AgentData.Status.UNATTACHABLE
        else -> it.value.status
      }
    }
    for (process in PROCESS_AGENT_MAP.keys) {
      val agentData = myTable.getAgentStatus(
        AgentStatusRequest.newBuilder().setPid(process.pid).setDeviceId(DataStorePollerTest.DEVICE.deviceId).build())
      assertThat(agentData.status).isEqualTo(newAgentStatusMap[process])
    }
  }

  private class FakeTransportService : TransportServiceGrpc.TransportServiceImplBase() {

    private var myProcessAgentMap: Map<Common.Process, Common.AgentData>? = null

    override fun getCurrentTime(request: TimeRequest, responseObserver: StreamObserver<TimeResponse>) {
      responseObserver.onNext(TimeResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    override fun getDevices(request: GetDevicesRequest, responseObserver: StreamObserver<GetDevicesResponse>) {
      responseObserver.onNext(GetDevicesResponse.newBuilder().addDevice(DataStorePollerTest.DEVICE).build())
      responseObserver.onCompleted()
    }

    override fun getProcesses(request: GetProcessesRequest, responseObserver: StreamObserver<GetProcessesResponse>) {
      myProcessAgentMap?.let {
        responseObserver.onNext(GetProcessesResponse.newBuilder().addAllProcess(it.keys).build())
      } ?: run {
        responseObserver.onNext(GetProcessesResponse.getDefaultInstance())
      }
      responseObserver.onCompleted()
    }

    override fun getAgentStatus(request: AgentStatusRequest, responseObserver: StreamObserver<Common.AgentData>) {

      myProcessAgentMap?.let {
        val process = it.keys.find { p -> p.pid == request.pid }
        responseObserver.onNext(it[process])
      } ?: run {
        responseObserver.onNext(Common.AgentData.getDefaultInstance())
      }
      responseObserver.onCompleted()
    }

    fun setProcessAgentMap(processAgentMap: Map<Common.Process, Common.AgentData>?) {
      myProcessAgentMap = processAgentMap
    }
  }

  companion object {
    private val PROCESS_1 = 11
    private val PROCESS_2 = 12
    private val PROCESS_3 = 13
    private val PROCESS_AGENT_MAP = mapOf(
      Common.Process.newBuilder().setPid(PROCESS_1).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
        Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build(),
      Common.Process.newBuilder().setPid(PROCESS_2).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
        Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.UNSPECIFIED).build(),
      Common.Process.newBuilder().setPid(PROCESS_3).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
        Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.UNATTACHABLE).build()
    )
  }
}