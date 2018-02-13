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

import com.android.tools.datastore.DataStorePollerTest
import com.android.tools.datastore.DataStoreService
import com.android.tools.datastore.TestGrpcService
import com.android.tools.datastore.energy.BatteryModel
import com.android.tools.datastore.energy.PowerProfile
import com.android.tools.datastore.service.EnergyService
import com.android.tools.profiler.proto.*
import com.google.common.collect.Lists
import io.grpc.stub.StreamObserver
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class EnergyDataPollerTest : DataStorePollerTest() {
  companion object {
    private val SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200)

    private val ONE_SEC_NS = TimeUnit.SECONDS.toNanos(1)
    private val ONE_SEC_MS = TimeUnit.SECONDS.toMillis(1)

    private val ONE_FOURTH_SEC_NS = ONE_SEC_NS / 4
    private val ONE_HALF_SEC_NS = ONE_SEC_NS / 2
    private val THREE_FOURTH_SEC_NS = ONE_SEC_NS * 3 / 4
    private val ONE_FOURTH_SEC_MS = ONE_SEC_MS / 4
    private val ONE_HALF_SEC_MS = ONE_SEC_MS / 2
    private val THREE_FOURTH_SEC_MS = ONE_SEC_MS * 3 / 4

    private val NETWORK_WIFI_STATE = NetworkProfiler.ConnectivityData.newBuilder()
      .setRadioState(NetworkProfiler.ConnectivityData.RadioState.HIGH)
      .setDefaultNetworkType(NetworkProfiler.ConnectivityData.NetworkType.WIFI)
      .build()

    private val NETWORK_RADIO_STATE = NetworkProfiler.ConnectivityData.newBuilder()
      .setRadioState(NetworkProfiler.ConnectivityData.RadioState.HIGH)
      .setDefaultNetworkType(NetworkProfiler.ConnectivityData.NetworkType.MOBILE)
      .build()
  }

  private class TestPowerProfile : PowerProfile {
    // Totally fake values, but chosen to make sure that different categories of power
    // will never overlap by accident.
    companion object {
      const val CPU_USAGE = 100 // 0 if [percent] == 0.0, max if [percent] == 1.0
      const val WIFI_ACTIVE = 1000
      const val RADIO_ACTIVE = 10000
    }

    override fun getCpuUsage(percent: Double): Int {
      return (CPU_USAGE * percent).toInt()
    }

    override fun getNetworkUsage(type: PowerProfile.NetworkType, state: PowerProfile.NetworkState): Int {
      return when (type) {
        PowerProfile.NetworkType.WIFI -> if (state == PowerProfile.NetworkState.ACTIVE) WIFI_ACTIVE else 0
        PowerProfile.NetworkType.RADIO -> if (state == PowerProfile.NetworkState.ACTIVE) RADIO_ACTIVE else 0
        else -> 0
      }
    }
  }

  /**
   * Run the clock forward and re-poll.
   */
  private fun fastForward(timeNs: Long) {
    fakeProfilerService.currentTimeNs += timeNs
    pollTicker.run()
  }

  private class FakeProfilerService : ProfilerServiceGrpc.ProfilerServiceImplBase() {
    var currentTimeNs: Long = 0

    override fun getCurrentTime(request: Profiler.TimeRequest, responseObserver: StreamObserver<Profiler.TimeResponse>) {
      responseObserver.onNext(Profiler.TimeResponse.newBuilder().setTimestampNs(currentTimeNs).build())
      responseObserver.onCompleted()
    }
  }

  private class FakeCpuService : CpuServiceGrpc.CpuServiceImplBase() {
    var dataList = ArrayList<CpuProfiler.CpuUsageData>()

    override fun getData(request: CpuProfiler.CpuDataRequest, responseObserver: StreamObserver<CpuProfiler.CpuDataResponse>) {
      responseObserver.onNext(CpuProfiler.CpuDataResponse.newBuilder().addAllData(dataList).build())
      responseObserver.onCompleted()
    }
  }

  private class FakeNetworkService : NetworkServiceGrpc.NetworkServiceImplBase() {
    var dataList = ArrayList<NetworkProfiler.NetworkProfilerData>()

    override fun getData(
      request: NetworkProfiler.NetworkDataRequest,
      responseObserver: StreamObserver<NetworkProfiler.NetworkDataResponse>
    ) {
      responseObserver.onNext(NetworkProfiler.NetworkDataResponse.newBuilder().addAllData(dataList).build())
      responseObserver.onCompleted()
    }
  }

  private class FakeEnergyService : EnergyServiceGrpc.EnergyServiceImplBase() {
    var eventList = ArrayList<EnergyProfiler.EnergyEvent>()

    override fun startMonitoringApp(
      request: EnergyProfiler.EnergyStartRequest,
      responseObserver: StreamObserver<EnergyProfiler.EnergyStartResponse>
    ) {
      responseObserver.onNext(EnergyProfiler.EnergyStartResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    override fun stopMonitoringApp(
      request: EnergyProfiler.EnergyStopRequest,
      responseObserver: StreamObserver<EnergyProfiler.EnergyStopResponse>
    ) {
      responseObserver.onNext(EnergyProfiler.EnergyStopResponse.getDefaultInstance())
      responseObserver.onCompleted()
    }

    override fun getEvents(request: EnergyProfiler.EnergyRequest, responseObserver: StreamObserver<EnergyProfiler.EnergyEventsResponse>) {
      responseObserver.onNext(EnergyProfiler.EnergyEventsResponse.newBuilder().addAllEvents(eventList).build())
      responseObserver.onCompleted()
    }
  }

  private val dataStoreService = mock(DataStoreService::class.java)
  private val energyService = EnergyService(BatteryModel(TestPowerProfile(), SAMPLE_INTERVAL_NS), dataStoreService, pollTicker::run)

  private val fakeProfilerService = FakeProfilerService() // Used to provide timestamp
  private val fakeCpuService = FakeCpuService() // Used to provide cpu data that affects energy usage
  private val fakeNetworkService = FakeNetworkService() // Used to provide test network data that affects energy usage
  private val fakeEnergyService = FakeEnergyService() // Host-side service calls fake device-side start/stop

  private val testName = TestName()

  private val grpcService = TestGrpcService(
    EnergyDataPollerTest::class.java,
    testName,
    energyService,
    fakeProfilerService,
    fakeCpuService,
    fakeNetworkService,
    fakeEnergyService
  )

  @get:Rule
  val chain = RuleChain.outerRule(testName).around(grpcService)

  @Before
  fun setUp() {
    `when`(dataStoreService.getProfilerClient(ArgumentMatchers.any())).thenReturn(ProfilerServiceGrpc.newBlockingStub(grpcService.channel))
    `when`(dataStoreService.getCpuClient(ArgumentMatchers.any())).thenReturn(CpuServiceGrpc.newBlockingStub(grpcService.channel))
    `when`(dataStoreService.getNetworkClient(ArgumentMatchers.any())).thenReturn(NetworkServiceGrpc.newBlockingStub(grpcService.channel))
    `when`(dataStoreService.getEnergyClient(ArgumentMatchers.any())).thenReturn(EnergyServiceGrpc.newBlockingStub(grpcService.channel))

    energyService.startMonitoringApp(
      EnergyProfiler.EnergyStartRequest.newBuilder().setSession(SESSION).build(),
      mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergyStartResponse>
    )
  }

  @After
  fun tearDown() {
    // Not strictly necessary to do this but it ensures we run all code paths
    energyService.stopMonitoringApp(
      EnergyProfiler.EnergyStopRequest.newBuilder().setSession(SESSION).build(),
      mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergyStopResponse>
    )
  }

  @Test
  fun defaultEnergyPollerReturnsEmptySamples() {
    fastForward(ONE_SEC_NS)

    val request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(0)
      .setEndTimestamp(ONE_SEC_NS)
      .build()

    val responseBuilder = EnergyProfiler.EnergySamplesResponse.newBuilder()
    for (timeNs in 0 until ONE_SEC_NS step SAMPLE_INTERVAL_NS) {
      // Intentionally leave energy usage values at 0
      responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(timeNs).build())
    }

    val responseObserver = mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergySamplesResponse>
    energyService.getSamples(request, responseObserver)
    validateResponse(responseObserver, responseBuilder.build())
  }

  @Test
  fun cpuEventsAffectEnergySamples() {
    fakeCpuService.dataList = Lists.newArrayList(
      CpuProfiler.CpuUsageData.newBuilder().setEndTimestamp(0).build(),
      // Timestamp rounds from 250ms to nearest bucket, 200ms. CPU at 100%
      CpuProfiler.CpuUsageData.newBuilder().setEndTimestamp(ONE_FOURTH_SEC_NS)
        .setElapsedTimeInMillisec(ONE_FOURTH_SEC_MS).setAppCpuTimeInMillisec(ONE_FOURTH_SEC_MS).build(),

      // Timestamp rounds from 750ms to nearest bucket, 800ms. CPU drops to 50%
      // (Since previous data, 500ms elapsed but only 250ms app time)
      CpuProfiler.CpuUsageData.newBuilder().setEndTimestamp(THREE_FOURTH_SEC_NS)
        .setElapsedTimeInMillisec(THREE_FOURTH_SEC_MS).setAppCpuTimeInMillisec(ONE_HALF_SEC_MS).build()
    )

    fastForward(ONE_SEC_NS)

    val request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(0)
      .setEndTimestamp(ONE_SEC_NS)
      .build()

    val responseBuilder = EnergyProfiler.EnergySamplesResponse.newBuilder()

    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(0 * SAMPLE_INTERVAL_NS).setCpuUsage(0).build())
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(1 * SAMPLE_INTERVAL_NS).setCpuUsage(TestPowerProfile.CPU_USAGE).build())
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(2 * SAMPLE_INTERVAL_NS).setCpuUsage(TestPowerProfile.CPU_USAGE).build())
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(3 * SAMPLE_INTERVAL_NS).setCpuUsage(TestPowerProfile.CPU_USAGE).build())
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(4 * SAMPLE_INTERVAL_NS).setCpuUsage(TestPowerProfile.CPU_USAGE / 2).build())

    val responseObserver = mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergySamplesResponse>
    energyService.getSamples(request, responseObserver)
    validateResponse(responseObserver, responseBuilder.build())
  }

  @Test
  fun wifiNetworkEventsAffectEnergySamples() {
    val speedDataTx = NetworkProfiler.SpeedData.newBuilder().setSent(1234).build()
    val speedDataRx = NetworkProfiler.SpeedData.newBuilder().setReceived(4321).build()
    val speedDataIdle = NetworkProfiler.SpeedData.getDefaultInstance()

    fakeNetworkService.dataList = Lists.newArrayList(
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(0).setConnectivityData(NETWORK_WIFI_STATE).build(),
      // Timestamp rounds from 250ms to nearest bucket, 200ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(ONE_FOURTH_SEC_NS).setSpeedData(speedDataTx).build(),
      // Timestamp rounds from 500ms to nearest bucket, 600ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(ONE_HALF_SEC_NS).setSpeedData(speedDataRx).build(),
      // Timestamp rounds from 750ms to nearest bucket, 800ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(THREE_FOURTH_SEC_NS).setSpeedData(speedDataIdle).build()
    )

    fastForward(ONE_SEC_NS)

    val request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(0)
      .setEndTimestamp(ONE_SEC_NS)
      .build()

    val responseBuilder = EnergyProfiler.EnergySamplesResponse.newBuilder()

    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(0 * SAMPLE_INTERVAL_NS).setNetworkUsage(0).build())
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(1 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.WIFI_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(2 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.WIFI_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(3 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.WIFI_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(4 * SAMPLE_INTERVAL_NS).setNetworkUsage(0).build())

    val responseObserver = mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergySamplesResponse>
    energyService.getSamples(request, responseObserver)
    validateResponse(responseObserver, responseBuilder.build())
  }

  @Test
  fun radioNetworkEventsAffectEnergySamples() {
    val speedDataTx = NetworkProfiler.SpeedData.newBuilder().setSent(1234).build()
    val speedDataRx = NetworkProfiler.SpeedData.newBuilder().setReceived(4321).build()
    val speedDataIdle = NetworkProfiler.SpeedData.getDefaultInstance()

    fakeNetworkService.dataList = Lists.newArrayList(
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(0).setConnectivityData(NETWORK_RADIO_STATE).build(),
      // Timestamp rounds from 250ms to nearest bucket, 200ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(ONE_FOURTH_SEC_NS).setSpeedData(speedDataTx).build(),
      // Timestamp rounds from 500ms to nearest bucket, 600ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(ONE_HALF_SEC_NS).setSpeedData(speedDataRx).build(),
      // Timestamp rounds from 750ms to nearest bucket, 800ms.
      NetworkProfiler.NetworkProfilerData.newBuilder().setEndTimestamp(THREE_FOURTH_SEC_NS).setSpeedData(speedDataIdle).build()
    )

    fastForward(ONE_SEC_NS)

    val request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(0)
      .setEndTimestamp(ONE_SEC_NS)
      .build()

    val responseBuilder = EnergyProfiler.EnergySamplesResponse.newBuilder()

    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(0 * SAMPLE_INTERVAL_NS).setNetworkUsage(0).build())
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(1 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.RADIO_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(2 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.RADIO_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(
      EnergyProfiler.EnergySample.newBuilder().setTimestamp(3 * SAMPLE_INTERVAL_NS).setNetworkUsage(
        TestPowerProfile.RADIO_ACTIVE
      ).build()
    )
    responseBuilder.addSamples(EnergyProfiler.EnergySample.newBuilder().setTimestamp(4 * SAMPLE_INTERVAL_NS).setNetworkUsage(0).build())

    val responseObserver = mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergySamplesResponse>
    energyService.getSamples(request, responseObserver)
    validateResponse(responseObserver, responseBuilder.build())
  }

  @Test
  fun eventsArePassedThrough() {
    val wakeLock1Acquire = EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(0)
      .setEventId(1)
      .setWakeLockAcquired(EnergyProfiler.WakeLockAcquired.getDefaultInstance())
      .build()

    val wakeLock2Acquire = EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(ONE_FOURTH_SEC_NS)
      .setEventId(2)
      .setWakeLockAcquired(EnergyProfiler.WakeLockAcquired.getDefaultInstance())
      .build()

    val wakeLock1Release = EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(ONE_HALF_SEC_NS)
      .setEventId(1)
      .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance())
      .build()

    val wakeLock2Release = EnergyProfiler.EnergyEvent.newBuilder()
      .setTimestamp(THREE_FOURTH_SEC_NS)
      .setEventId(2)
      .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance())
      .build()

    fakeEnergyService.eventList = Lists.newArrayList(
      wakeLock1Acquire,
      wakeLock2Acquire,
      wakeLock1Release,
      wakeLock2Release
    )

    fastForward(ONE_SEC_NS)

    val request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(SESSION)
      .setStartTimestamp(0)
      .setEndTimestamp(ONE_SEC_NS)
      .build()

    val responseBuilder = EnergyProfiler.EnergyEventsResponse.newBuilder()

    responseBuilder.addEvents(wakeLock1Acquire)
    responseBuilder.addEvents(wakeLock2Acquire)
    responseBuilder.addEvents(wakeLock1Release)
    responseBuilder.addEvents(wakeLock2Release)

    val responseObserver = mock(StreamObserver::class.java) as StreamObserver<EnergyProfiler.EnergyEventsResponse>
    energyService.getEvents(request, responseObserver)
    validateResponse(responseObserver, responseBuilder.build())
  }
}