/*
 * Copyright (C) 2023 The Android Open Source Project
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
package stats

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceInfo
import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceIcons
import com.android.tools.idea.execution.common.stats.getDeviceInfo
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn

class RunStatsUtilsTest {

  @get:Rule val projectRule = ProjectRule()

  private val mockIDevice: IDevice = mock()
  private val deviceProvisionerService: DeviceProvisionerService = mock()
  private val deviceProvisioner: DeviceProvisioner = mock()
  private val properties = DeviceProperties.build {
    isRemote = true
    isVirtual = false
    icon = StudioDefaultDeviceIcons.handheld
    populateDeviceInfoProto("TestPlugin", "localhost:12345", emptyMap(), "connectionId")
  }
  private val deviceHandle = object : DeviceHandle {
    override val id = DeviceId("", false, "")
    override val scope: CoroutineScope
      get() = TODO("Not yet implemented")
    override val stateFlow: StateFlow<DeviceState>
      get() = _stateFlow

    private val _stateFlow: MutableStateFlow<DeviceState> = MutableStateFlow(DeviceState.Disconnected(properties))

    fun setState(state: DeviceState) = _stateFlow.update { state }
  }
  private val connectedDevice = object : ConnectedDevice {
    override val session: AdbSession
      get() = TODO("Not yet implemented")
    override val cache: CoroutineScopeCache
      get() = TODO("Not yet implemented")
    override val deviceInfoFlow: StateFlow<DeviceInfo>
      get() = _deviceInfoFlow
    private val _deviceInfoFlow: MutableStateFlow<DeviceInfo> = MutableStateFlow(DeviceInfo("", mock()))

    fun setDeviceInfo(info: DeviceInfo) = _deviceInfoFlow.update { info }
  }

  @Before
  fun setup() {
    projectRule.project.replaceService(
      DeviceProvisionerService::class.java,
      deviceProvisionerService,
      projectRule.disposable
    )
    doReturn(deviceProvisioner).whenever(deviceProvisionerService).deviceProvisioner
    val mockDeviceListStateFlow: StateFlow<List<DeviceHandle>> = mock()
    doReturn(mockDeviceListStateFlow).whenever(deviceProvisioner).devices
    val deviceHandleList = listOf(deviceHandle)
    doReturn(deviceHandleList).whenever(mockDeviceListStateFlow).value
  }
  @Test
  fun testGetDeviceInfoWhenIDeviceMatchesConnectedDeviceHandle() {
    mockIDevice.setSerialNumber("localhost:12345")
    connectedDevice.setDeviceInfo(
      DeviceInfo("localhost:12345", com.android.adblib.DeviceState.ONLINE)
    )
    deviceHandle.setState(
      DeviceState.Connected(properties, connectedDevice)
    )
    val deviceInfo = getDeviceInfo(mockIDevice, projectRule.project)
    assertThat(deviceInfo.deviceProvisionerId).isNotEmpty()
    assertThat(deviceInfo.deviceProvisionerId).isEqualTo("TestPlugin")
    assertThat(deviceInfo.deviceType).isEqualTo(DeviceType.CLOUD_PHYSICAL)
  }

  @Test
  fun testGetDeviceInfoWhenIDeviceMatchesDisconnectedDeviceHandle() {
    mockIDevice.setSerialNumber("localhost:12345")

    val deviceInfo = getDeviceInfo(mockIDevice, projectRule.project)
    // IDevice does not set device_provisioner_id
    assertThat(deviceInfo.hasDeviceProvisionerId()).isFalse()
    assertThat(deviceInfo.deviceProvisionerId).isEmpty()
    assertThat(deviceInfo.deviceType).isEqualTo(DeviceType.LOCAL_PHYSICAL)
  }

  @Test
  fun testGetDeviceInfoWhenIDeviceDoesNotMatchDeviceHandle() {
    mockIDevice.setSerialNumber("localhost:23456")
    val deviceInfo = getDeviceInfo(mockIDevice, projectRule.project)
    // IDevice does not set device_provisioner_id
    assertThat(deviceInfo.hasDeviceProvisionerId()).isFalse()
    assertThat(deviceInfo.deviceProvisionerId).isEmpty()
    assertThat(deviceInfo.deviceType).isEqualTo(DeviceType.LOCAL_PHYSICAL)
  }

  private fun IDevice.setSerialNumber(serialNumber: String) {
    doReturn(serialNumber).whenever(this).serialNumber
  }
}