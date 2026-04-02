/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.avd.glassespairing

import com.android.adblib.ConnectedDevice
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.tools.aiglasses.ShellCommandException
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.sdklib.deviceprovisioner.PairedGlassesInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avd.StudioLocalEmulatorDeviceHandle
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GlassesPairingStateManagerTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val fakeAdbRule = FakeAdbServerProviderRule()

  @Before
  fun setUp() {
    val lockService = service<GlassesPairingLockService>()
    if (lockService.discoveryLock.isLocked) {
      lockService.discoveryLock.unlock()
    }
    lockService.setWizardOpen(false)
  }

  @Test
  fun testInitializeCallsReconcile() = runTest {
    var proberCalled = false
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val stateManager = GlassesPairingStateManager(projectRule.project, backgroundScope)
    stateManager.ioDispatcher = testDispatcher
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          proberCalled = true
          return null
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    setupMockProvisionerWithOrphanGlasses()

    stateManager.initialize()
    testScheduler.advanceTimeBy(2000)

    assertTrue(proberCalled)
  }

  @Test
  fun testCycleDurationsWindowCapsAt5() = runTest {
    val testTimeSource = TestTimeSource()
    val stateManager = GlassesPairingStateManager(projectRule.project, backgroundScope)
    stateManager.ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    stateManager.timeSource = testTimeSource
    stateManager.initialize()

    // Advance time by 120 seconds to allow at least 6 iterations.
    // (1s + 2s + 4s + 8s + 16s = 31s), ensuring we hit the cap of 5.
    testScheduler.advanceTimeBy(120_000)
    testScheduler.runCurrent()

    assertTrue(stateManager.cycleDurations.size == 5)
  }

  @Test
  fun testLeaderElection() = runTest {
    var proberCallCount1 = 0
    var proberCallCount2 = 0
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope1 = CoroutineScope(testDispatcher)
    val scope2 = CoroutineScope(testDispatcher)

    val stateManager1 = GlassesPairingStateManager(projectRule.project, scope1)
    stateManager1.ioDispatcher = testDispatcher
    stateManager1.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          proberCallCount1++
          return null
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    val stateManager2 = GlassesPairingStateManager(projectRule.project, scope2)
    stateManager2.ioDispatcher = testDispatcher
    stateManager2.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          proberCallCount2++
          return null
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    setupMockProvisionerWithOrphanGlasses()

    try {
      stateManager1.initialize()
      stateManager2.initialize()

      testScheduler.advanceTimeBy(2000)

      // Only one should have run!
      assertTrue((proberCallCount1 == 1 && proberCallCount2 == 0) || (proberCallCount1 == 0 && proberCallCount2 == 1))

      // Now cancel the leader!
      if (proberCallCount1 == 1) {
        scope1.cancel()
      } else {
        scope2.cancel()
      }

      testScheduler.advanceTimeBy(2000)

      // The other one should take over!
      assertTrue(proberCallCount1 + proberCallCount2 == 2)
    } finally {
      scope1.cancel()
      scope2.cancel()
    }
  }

  @Test
  fun testWizardPreemption() = runTest {
    var proberCallCount = 0
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val stateManager = GlassesPairingStateManager(projectRule.project, backgroundScope)
    stateManager.ioDispatcher = testDispatcher
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          proberCallCount++
          return null
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    setupMockProvisionerWithOrphanGlasses()

    val lockService = service<GlassesPairingLockService>()

    stateManager.initialize()
    testScheduler.advanceTimeBy(2000)
    assertTrue(proberCallCount == 1)

    // Now simulate wizard opening
    lockService.setWizardOpen(true)

    testScheduler.advanceTimeBy(10000)
    assertTrue(proberCallCount == 1) // Should not increment!

    // Now wizard closes
    lockService.setWizardOpen(false)
    testScheduler.runCurrent()

    testScheduler.advanceTimeBy(10000)
    assertTrue(proberCallCount >= 2) // Should increment at least once!
  }

  @Test
  fun testReconcileStateSkipsIfWizardOpen() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val stateManager = GlassesPairingStateManager(projectRule.project, backgroundScope)
    stateManager.ioDispatcher = testDispatcher

    val lockService = service<GlassesPairingLockService>()
    lockService.setWizardOpen(true)

    try {
      val result = stateManager.reconcileState()
      assertFalse(result)
    } finally {
      lockService.setWizardOpen(false)
    }
  }

  @Test
  fun testDeviceTrulyBondedParsingTrimsInput() {
    assertTrue(DeviceTrulyBonded.fromString("TRULY_BONDED ") == DeviceTrulyBonded.TRULY_BONDED)
    assertTrue(DeviceTrulyBonded.fromString(" TRULY_BONDED\n") == DeviceTrulyBonded.TRULY_BONDED)
    assertTrue(DeviceTrulyBonded.fromString("\r\nNOT_TRULY_BONDED") == DeviceTrulyBonded.NOT_TRULY_BONDED)
    assertTrue(DeviceTrulyBonded.fromString(" RANDOM_OUTPUT ") == DeviceTrulyBonded.UNKNOWN)
  }

  /**
   * Tests Phase 1 & 2 of reconcileState(): Auto-importing linkage via ADB. We use an anonymous subclass to mock getBluetoothAddress and
   * checkBondState to avoid complex adblib mocking.
   */
  @Test
  fun testReconcileStateAutoImportsLinkage() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val fakeAdbSession = FakeAdbSession()
    val mockAdbLibService = mock<AdbLibService>()
    whenever(mockAdbLibService.session).thenReturn(fakeAdbSession)
    projectRule.project.replaceService(AdbLibService::class.java, mockAdbLibService, projectRule.project)

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()

    val glassesDevice = mock<ConnectedDevice>()
    val phoneDevice = mock<ConnectedDevice>()

    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = glassesDevice,
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = emptyList()
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = phoneDevice,
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val devicesFlow = MutableStateFlow(listOf(glassesHandle, phoneHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val expectedMac = "00:11:22:33:44:55"
    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = expectedMac

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.TRULY_BONDED
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertTrue(result)
    verify(phoneHandle).addPairedGlasses(DeviceId("Fake", false, "glasses1"), expectedMac)

    scope.cancel()
  }

  /**
   * Tests Phase 3 of reconcileState(): Pruning dead entries. We use an anonymous subclass to mock checkBondState to avoid complex adblib
   * mocking.
   */
  @Test
  fun testReconcileStatePrunesDeadConnections() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val fakeAdbSession = FakeAdbSession()
    val mockAdbLibService = mock<AdbLibService>()
    whenever(mockAdbLibService.session).thenReturn(fakeAdbSession)
    projectRule.project.replaceService(AdbLibService::class.java, mockAdbLibService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneDevice = mock<ConnectedDevice>()

    val glassesInfo = PairedGlassesInfo(DeviceId("Fake", false, "glasses1"), "00:11:22:33:44:55")

    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = listOf(glassesInfo)
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = phoneDevice,
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val devicesFlow = MutableStateFlow(listOf(phoneHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.NOT_TRULY_BONDED
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertTrue(result)
    verify(phoneHandle).removePairedGlasses(DeviceId("Fake", false, "glasses1"))

    scope.cancel()
  }

  @Test
  fun testReconcileState_nullBondCount_doesNotPrune() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockAdbLibService = mock<AdbLibService>()
    val fakeAdbSession = FakeAdbSession()
    whenever(mockAdbLibService.session).thenReturn(fakeAdbSession)
    projectRule.project.replaceService(AdbLibService::class.java, mockAdbLibService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneDevice = mock<ConnectedDevice>()

    val glassesInfo = PairedGlassesInfo(DeviceId("Fake", false, "glasses1"), null)

    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = listOf(glassesInfo)
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = phoneDevice,
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock<ConnectedDevice>(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val devicesFlow = MutableStateFlow(listOf(phoneHandle, glassesHandle))
    val mockService = mock<DeviceProvisionerService>()
    val mockProvisioner = mock<DeviceProvisioner>()
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN

        override suspend fun getPairedDeviceCount(device: DeviceHandle): Int? = null
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertFalse(result)
    verify(phoneHandle, never()).removePairedGlasses(any())
    scope.cancel()
  }

  private fun setupMockProvisionerWithOrphanGlasses(): DeviceProvisioner {
    val mockProvisioner = mock<DeviceProvisioner>()
    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val properties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = properties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock<ConnectedDevice>(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val devicesFlow = MutableStateFlow(listOf(glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val mockService: DeviceProvisionerService = mock<DeviceProvisionerService>()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    return mockProvisioner
  }

  @Test
  fun testReconcileStatePrunesWipedPhone() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val fakeAdbSession = FakeAdbSession()
    val mockAdbLibService = mock<AdbLibService>()
    whenever(mockAdbLibService.session).thenReturn(fakeAdbSession)
    projectRule.project.replaceService(AdbLibService::class.java, mockAdbLibService, projectRule.project)

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()

    val phoneId = DeviceId("Fake", false, "phone1")

    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
        pairedPhoneId = phoneId
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = emptyList()
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(phoneId)

    val devicesFlow = MutableStateFlow(listOf(glassesHandle, phoneHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertTrue(result)
    verify(glassesHandle).updatePairedPhone(null)

    scope.cancel()
  }

  @Test
  fun testReconcileStateAbortsOnDeviceChange() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val properties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = properties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val devicesFlow = MutableStateFlow(listOf(glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          // Simulate state change during Phase 1
          devicesFlow.value = emptyList()
          return "00:11:22:33:44:55"
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertFalse(result)

    scope.cancel()
  }

  @Test
  fun testReconcileStateSkipsOfflineDevices() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()

    val glassesInfo = PairedGlassesInfo(DeviceId("Fake", false, "glasses1"), "00:11:22:33:44:55")

    val properties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = listOf(glassesInfo)
      }
    val phoneState =
      DeviceState.Disconnected(properties = properties, isTransitioning = false, status = "Offline", reservation = null, error = null)
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))
    val glassesProperties = mock<DeviceProperties>()
    whenever(glassesProperties.deviceType).thenReturn(DeviceType.AI_GLASSES)
    val glassesState =
      DeviceState.Disconnected(
        properties = glassesProperties,
        isTransitioning = false,
        status = "Offline",
        reservation = null,
        error = null,
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    val devicesFlow = MutableStateFlow(listOf(phoneHandle, glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.NOT_TRULY_BONDED
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertFalse(result)
    // Should NOT prune because phone is offline
    verify(phoneHandle, never()).removePairedGlasses(any())

    scope.cancel()
  }

  @Test
  fun testReconcileStateSkipsTransitioningDevices() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()

    val glassesInfo = PairedGlassesInfo(DeviceId("Fake", false, "glasses1"), "00:11:22:33:44:55")

    val properties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = listOf(glassesInfo)
      }
    val phoneState =
      DeviceState.Connected(properties = properties, isTransitioning = true, isReady = true, status = "Connected", connectedDevice = mock())
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))
    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
        pairedPhoneId = DeviceId("Fake", false, "phone1")
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    val devicesFlow = MutableStateFlow(listOf(phoneHandle, glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.TRULY_BONDED
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertFalse(result)
    // Should NOT prune because phone is transitioning
    verify(phoneHandle, never()).removePairedGlasses(any())

    scope.cancel()
  }

  @Test
  fun testTriggerDrainingSkipsRemainingSleep() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)
    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.ioDispatcher = testDispatcher
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    setupMockProvisionerWithOrphanGlasses()

    stateManager.initialize()

    // Let it run once to fill cycleDurations and set baseline
    testScheduler.runCurrent()

    // Now it should be sleeping for baseline (1000ms)
    // Send a trigger during the baseline sleep
    testScheduler.advanceTimeBy(500)
    stateManager.triggerReconcile()

    var proberCallCount = 0
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          proberCallCount++
          return null
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN
      }

    // Now advance by another 500 to complete baseline sleep
    testScheduler.advanceTimeBy(500)

    // It should have woken up and started another cycle immediately,
    // instead of waiting for the full currentDelay (which is 2000ms due to backoff).

    // Advance time by just 1ms to see if it runs
    testScheduler.advanceTimeBy(1)

    try {
      assertTrue(proberCallCount == 1)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun testReconcileStateHandlesShellCommandExceptionInGetPairedDeviceCount() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = listOf(PairedGlassesInfo(DeviceId("Fake", false, "glasses1"), null))
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val devicesFlow = MutableStateFlow(listOf(phoneHandle, glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? = null

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.UNKNOWN

        override suspend fun getPairedDeviceCount(device: DeviceHandle): Int {
          throw ShellCommandException("Fake failure")
        }
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()

    assertFalse(result) // Should not crash and return false (no changes)

    scope.cancel()
  }

  @Test
  fun testReconcileStateHandlesTimeoutInGetBluetoothAddress() = runTest {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    val scope = CoroutineScope(testDispatcher)

    val mockService: DeviceProvisionerService = mock()
    val mockProvisioner: DeviceProvisioner = mock()
    whenever(mockService.deviceProvisioner).thenReturn(mockProvisioner)
    projectRule.project.replaceService(DeviceProvisionerService::class.java, mockService, projectRule.project)

    val phoneHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val phoneDevice = mock<ConnectedDevice>()

    val phoneProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "Pixel 9"
        deviceType = DeviceType.HANDHELD
        androidVersion = AndroidVersion(36, 1)
        pairedGlassesInfos = emptyList()
      }
    val phoneState =
      DeviceState.Connected(
        properties = phoneProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = phoneDevice,
      )
    whenever(phoneHandle.state).thenReturn(phoneState)
    whenever(phoneHandle.id).thenReturn(DeviceId("Fake", false, "phone1"))

    val glassesHandle = mock<StudioLocalEmulatorDeviceHandle>()
    val glassesProperties =
      DeviceProperties.buildForTest {
        icon = EmptyIcon.DEFAULT
        manufacturer = "Google"
        model = "AI Glasses"
        deviceType = DeviceType.AI_GLASSES
        androidVersion = AndroidVersion(36, 1)
      }
    val glassesState =
      DeviceState.Connected(
        properties = glassesProperties,
        isTransitioning = false,
        isReady = true,
        status = "Connected",
        connectedDevice = mock(),
      )
    whenever(glassesHandle.state).thenReturn(glassesState)
    whenever(glassesHandle.id).thenReturn(DeviceId("Fake", false, "glasses1"))

    val devicesFlow = MutableStateFlow(listOf(phoneHandle, glassesHandle))
    whenever(mockProvisioner.devices).thenReturn(devicesFlow)

    val stateManager = GlassesPairingStateManager(projectRule.project, scope)
    stateManager.adbProber =
      object : AdbProber {
        override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
          delay(5000L) // Simulate hang
          return "00:11:22:33:44:55"
        }

        override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded = DeviceTrulyBonded.TRULY_BONDED
      }
    stateManager.ioDispatcher = testDispatcher

    val result = stateManager.reconcileState()
    assertFalse(result) // Should not make changes because it timed out!

    scope.cancel()
  }
}
