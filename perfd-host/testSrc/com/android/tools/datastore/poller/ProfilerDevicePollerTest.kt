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

import com.android.testutils.TestUtils
import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.DataStorePollerTest
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE
import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.database.ProfilerTable
import com.android.tools.datastore.poller.ProfilerDevicePoller.AGENT_WAIT_RETRY_THRESHOLD
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profiler.proto.ProfilerServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProfilerDevicePollerTest : DataStorePollerTest() {

  private lateinit var myDataStore: DataStoreService
  private lateinit var myProfilerService: FakeProfilerService
  private lateinit var myDatabase: DataStoreDatabase
  private lateinit var myTable: ProfilerTable
  private lateinit var myServer: Server
  private lateinit var myManagedChannel: ManagedChannel
  private lateinit var myServiceStub: ProfilerServiceGrpc.ProfilerServiceBlockingStub
  private lateinit var myPoller: ProfilerDevicePoller

  @Before
  fun setup() {
    val servicePath = TestUtils.createTempDirDeletedOnExit().absolutePath
    myDataStore = DataStoreService(javaClass.simpleName, servicePath, pollTicker::run, FakeLogService())
    myProfilerService = FakeProfilerService()
    val namespace = DEFAULT_SHARED_NAMESPACE
    myDatabase = myDataStore.createDatabase(servicePath + namespace.myNamespace, namespace.myCharacteristic) { _ -> }
    myTable = ProfilerTable()
    myTable.initialize(myDatabase.connection)

    myServer = InProcessServerBuilder.forName("ProfilerDevicePollerServer").addService(myProfilerService).build()
    myServer.start()
    myManagedChannel = InProcessChannelBuilder.forName("ProfilerDevicePollerServer").build()
    myServiceStub = ProfilerServiceGrpc.newBlockingStub(myManagedChannel)

    myPoller = ProfilerDevicePoller(myDataStore, myTable, myServiceStub)
    // Stops the poller as that's dependent on system clock. We need to manually test the poll method.
    myPoller.stop()
  }

  @After
  fun teardown() {
    myServer.shutdownNow()
    myDataStore.shutdown()
  }

  @Test
  fun testPollingAddsDeviceAndProcessesToDatabase() {
    val processRequest = Profiler.GetProcessesRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId).build()
    assertThat(myTable.devices.deviceList).isEmpty()
    assertThat(myTable.getProcesses(processRequest).processList).isEmpty()

    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myPoller.poll()

    assertThat(myTable.devices.deviceCount).isEqualTo(1)
    assertThat(myTable.devices.getDevice(0)).isEqualTo(DataStorePollerTest.DEVICE)

    val processResponse = myTable.getProcesses(processRequest)
    assertThat(processResponse.processList.size).isEqualTo(3)
    assertThat(processResponse.processList).containsExactlyElementsIn(PROCESS_AGENT_MAP.keys)
  }

  @Test
  fun testPollingUpdateDeadProcesses() {
    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myPoller.poll()
    myProfilerService.setProcessAgentMap(null)
    myPoller.poll()

    assertThat(myTable.devices.deviceCount).isEqualTo(1)
    assertThat(myTable.devices.getDevice(0)).isEqualTo(DataStorePollerTest.DEVICE)

    val processRequest = Profiler.GetProcessesRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId).build()
    val processResponse = myTable.getProcesses(processRequest)
    assertThat(processResponse.processList.size).isEqualTo(3)
    val deadProcessSet = PROCESS_AGENT_MAP.keys.map { p -> p.toBuilder().setState(Common.Process.State.DEAD).build() }
    assertThat(processResponse.processList).containsAllIn(deadProcessSet)
  }

  @Test
  fun testAgentIncompatibleAfterRetryThreshold() {
    myProfilerService.setProcessAgentMap(PROCESS_AGENT_MAP)
    myPoller.poll()

    val agentRequestBuilder = Profiler.AgentStatusRequest.newBuilder().setDeviceId(DataStorePollerTest.DEVICE.deviceId)
    var statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_1).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.ATTACHED)
    assertThat(statusResponse.isAgentAttachable).isEqualTo(true)
    statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_2).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.DETACHED)
    assertThat(statusResponse.isAgentAttachable).isEqualTo(true)
    statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_3).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.DETACHED)
    assertThat(statusResponse.isAgentAttachable).isEqualTo(false)

    for (i in 0 until AGENT_WAIT_RETRY_THRESHOLD) {
      myPoller.poll()
    }

    statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_1).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.ATTACHED)
    assertThat(statusResponse.isAgentAttachable).isEqualTo(true)
    statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_2).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.DETACHED)
    // The poller should mark the detached but agent-compatible process as agent-incompatible after max retries.
    assertThat(statusResponse.isAgentAttachable).isEqualTo(false)
    statusResponse = myTable.getAgentStatus(agentRequestBuilder.setPid(PROCESS_3).build())
    assertThat(statusResponse.status).isEqualTo(Profiler.AgentStatusResponse.Status.DETACHED)
    assertThat(statusResponse.isAgentAttachable).isEqualTo(false)
  }

  private class FakeProfilerService : ProfilerServiceGrpc.ProfilerServiceImplBase() {

    private var myProcessAgentMap: Map<Common.Process, Profiler.AgentStatusResponse>? = null

    override fun getCurrentTime(request: Profiler.TimeRequest, responseObserver: StreamObserver<Profiler.TimeResponse>) {
      responseObserver.onNext(Profiler.TimeResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    override fun getDevices(request: Profiler.GetDevicesRequest, responseObserver: StreamObserver<Profiler.GetDevicesResponse>) {
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(DataStorePollerTest.DEVICE).build())
      responseObserver.onCompleted()
    }

    override fun getProcesses(request: Profiler.GetProcessesRequest, responseObserver: StreamObserver<Profiler.GetProcessesResponse>) {
      myProcessAgentMap?.let {
        responseObserver.onNext(Profiler.GetProcessesResponse.newBuilder().addAllProcess(it.keys).build())
      } ?: run {
        responseObserver.onNext(Profiler.GetProcessesResponse.getDefaultInstance())
      }
      responseObserver.onCompleted()
    }

    override fun getAgentStatus(request: Profiler.AgentStatusRequest, responseObserver: StreamObserver<Profiler.AgentStatusResponse>) {

      myProcessAgentMap?.let {
        val process = it.keys.find { p -> p.pid == request.pid }
        responseObserver.onNext(it[process])
      } ?: run {
        responseObserver.onNext(Profiler.AgentStatusResponse.getDefaultInstance())
      }
      responseObserver.onCompleted()
    }

    fun setProcessAgentMap(processAgentMap: Map<Common.Process, Profiler.AgentStatusResponse>?) {
      myProcessAgentMap = processAgentMap
    }
  }

  companion object {
    private val PROCESS_1 = 11
    private val PROCESS_2 = 12
    private val PROCESS_3 = 13
    private val PROCESS_AGENT_MAP = mapOf(
      Common.Process.newBuilder().setPid(PROCESS_1).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
      Profiler.AgentStatusResponse.newBuilder().setIsAgentAttachable(true).setStatus(Profiler.AgentStatusResponse.Status.ATTACHED).build(),
      Common.Process.newBuilder().setPid(PROCESS_2).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
      Profiler.AgentStatusResponse.newBuilder().setIsAgentAttachable(true).setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build(),
      Common.Process.newBuilder().setPid(PROCESS_3).setDeviceId(DEVICE.deviceId).setState(Common.Process.State.ALIVE).build() to
      Profiler.AgentStatusResponse.newBuilder().setIsAgentAttachable(false).setStatus(Profiler.AgentStatusResponse.Status.DETACHED).build()
    )
  }
}