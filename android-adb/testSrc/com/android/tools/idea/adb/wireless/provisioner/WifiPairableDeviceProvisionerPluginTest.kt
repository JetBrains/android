/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless.provisioner

import com.android.adblib.AdbFeatures
import com.android.adblib.MdnsServices
import com.android.adblib.MdnsTlsService
import com.android.adblib.MdnsTrackServiceInfo
import com.android.adblib.ServerStatus
import com.android.adblib.ServiceInstanceName
import com.android.tools.idea.adb.wireless.AdbCommandResult
import com.android.tools.idea.adb.wireless.AdbOnlineDevice
import com.android.tools.idea.adb.wireless.AdbServiceWrapper
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.adb.wireless.PairingResult
import com.android.tools.idea.adb.wireless.TrackingMdnsService
import com.android.tools.idea.adb.wireless.WiFiPairingController
import com.android.tools.idea.adb.wireless.v2.ui.WifiPairableDevicesPersistentStateComponent
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.common.waitUntil
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunsInEdt
@ExperimentalCoroutinesApi
class WifiPairableDeviceProvisionerPluginTest {

  private val adbService = FakeAdbServiceWrapper()
  private lateinit var pairingController: WiFiPairingController
  private val pairDevicesService = mock<PairDevicesUsingWiFiService>()
  private val mockPersistentService = mock<WifiPairableDevicesPersistentStateComponent>()

  private val projectRule = ProjectRule()
  val project: Project
    get() = projectRule.project

  @get:Rule
  val rule =
    RuleChain(
      EdtRule(),
      ApplicationRule(),
      projectRule,
      ApplicationServiceRule(
        WifiPairableDevicesPersistentStateComponent::class.java,
        mockPersistentService,
      ),
      ProjectServiceRule(projectRule, PairDevicesUsingWiFiService::class.java, pairDevicesService),
    )

  private val mdnsFlow = MutableStateFlow(MdnsServices(emptyList(), emptyList(), emptyList()))

  @Before
  fun setUp() {
    pairingController = mock()
    whenever(pairDevicesService.createPairingDialogController(any())).thenReturn(pairingController)
    adbService.setMdnsTrackServicesFlow(mdnsFlow)
    adbService.setHostFeatures(listOf(AdbFeatures.TRACK_MDNS_SERVICE))
    doReturn(MutableStateFlow(emptySet<String>())).whenever(mockPersistentService).hiddenDevices
  }

  @Test
  fun pluginDoesNothing_whenMdnsTrackingNotSupported() = runTest {
    adbService.setHostFeatures(emptyList())
    mdnsFlow.value = createMdnsTlsService("service1")
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(plugin.devices.value).isEmpty()
  }

  @Test
  fun newMdnsService_createsDeviceHandle() = runTest {
    mdnsFlow.value = createMdnsTlsService("service1")
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(plugin.devices.value).hasSize(1)
    val handle =
      plugin.devices.value.first() as WifiPairableDeviceProvisionerPlugin.WifiPairableDeviceHandle
    assertThat(handle.serviceName).isEqualTo("service1")
    assertThat(handle.state.properties.model).isEqualTo("Pixel 8 at 192.168.1.100:4321")
  }

  @Test
  fun newMdnsService_withNullModel_createsDeviceHandleWithFallbackName() = runTest {
    mdnsFlow.value = createMdnsTlsService("service1", model = null)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(plugin.devices.value).hasSize(1)
    val handle =
      plugin.devices.value.first() as WifiPairableDeviceProvisionerPlugin.WifiPairableDeviceHandle
    assertThat(handle.serviceName).isEqualTo("service1")
    assertThat(handle.state.properties.model).isEqualTo("Device at 192.168.1.100:4321")
  }

  @Test
  fun knownDevices_areIgnored() = runTest {
    mdnsFlow.value = createMdnsTlsService("service1", knownDevice = true)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(plugin.devices.value).isEmpty()
  }

  @Test
  fun hiddenDevices_areIgnored() = runTest {
    doReturn(MutableStateFlow(setOf("service1"))).whenever(mockPersistentService).hiddenDevices
    mdnsFlow.value = createMdnsTlsService("service1")
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(plugin.devices.value).isEmpty()
  }

  @Test
  fun pairAction_launchesPairingDialog() = runTest {
    mdnsFlow.value = createMdnsTlsService("service1", "My Pixel", "1.2.3.4", 1234)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    val handle = plugin.devices.value.first()
    handle.wifiPairDeviceAction!!.pair()

    verify(pairDevicesService)
      .createPairingDialogController(
        argThat { s: TrackingMdnsService ->
          s.serviceName == "service1" &&
            s.deviceName == "My Pixel" &&
            s.ipv4 == "1.2.3.4" &&
            s.port == "1234"
        }
      )
    verify(pairingController).showDialog()
  }

  @Test
  fun handleScopeIsCancelled_onRemoval() = runTest {
    mdnsFlow.value = createMdnsTlsService("service1", "My Pixel", "1.2.3.4", 1234)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    val handle = plugin.devices.value.first()
    assertThat(handle.scope.isActive).isTrue()

    mdnsFlow.value = MdnsServices(emptyList(), emptyList(), emptyList())
    waitUntil { !handle.scope.isActive }
    assertThat(plugin.devices.value).isEmpty()
  }

  @Test
  fun mdnsTracking_retriesOnError() = runTest {
    var attempt = 0
    val failingFlow = flow {
      if (attempt++ == 0) {
        throw IOException("ADB connection failed")
      } else {
        emit(createMdnsTlsService("service1"))
      }
    }
    adbService.setMdnsTrackServicesFlow(failingFlow)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(5500) // Initial delay

    assertThat(plugin.devices.value).isEmpty()

    advanceTimeBy(1100) // Retry delay

    assertThat(plugin.devices.value).hasSize(1)
    assertThat(attempt).isEqualTo(2)
  }

  @Test
  fun mdnsTracking_doesNotRetryOnCancellation() = runTest {
    var attempts = 0
    val flow =
      flow<MdnsServices> {
        attempts++
        throw CancellationException()
      }
    adbService.setMdnsTrackServicesFlow(flow)
    val plugin = WifiPairableDeviceProvisionerPlugin(backgroundScope, adbService, project)
    advanceTimeBy(6000) // Past initial delay

    assertThat(attempts).isEqualTo(1)
    assertThat(plugin.devices.value).isEmpty()
  }

  private class FakeAdbServiceWrapper : AdbServiceWrapper {
    private var hostFeatures = emptyList<String>()
    private var mdnsFlow: Flow<MdnsServices> = flowOf()

    fun setHostFeatures(features: List<String>) {
      hostFeatures = features
    }

    fun setMdnsTrackServicesFlow(flow: Flow<MdnsServices>) {
      mdnsFlow = flow
    }

    override suspend fun getHostFeatures(): List<String> = hostFeatures

    override fun trackMdnsServices(): Flow<MdnsServices> = mdnsFlow

    override suspend fun executeCommand(args: List<String>, stdin: String): AdbCommandResult {
      TODO("Not implemented")
    }

    override suspend fun waitForOnlineDevice(pairingResult: PairingResult): AdbOnlineDevice {
      TODO("Not implemented")
    }

    override suspend fun getServerStatus(): ServerStatus {
      TODO("Not implemented")
    }
  }

  private fun createMdnsTlsService(
    instanceName: String,
    model: String? = "Pixel 8",
    ipv4: String = "192.168.1.100",
    port: Int = 4321,
    knownDevice: Boolean = false,
    sdk: String = "34",
  ): MdnsServices {
    val serviceInfo =
      MdnsTrackServiceInfo(
        serviceInstanceName = ServiceInstanceName(instanceName, "_adb-tls-connect._tcp", "local"),
        deviceModel = model,
        ipv4 = ipv4,
        port = port,
        ipv6 = emptyList(),
        buildVersionSdkFull = sdk,
      )
    val services = listOf(MdnsTlsService(serviceInfo, knownDevice))
    return MdnsServices(emptyList(), services, emptyList())
  }
}
