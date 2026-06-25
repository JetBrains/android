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
package com.android.tools.idea.devicemanagerv2.details

import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.PairedGlassesInfo
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.EDT
import icons.StudioIcons
import java.awt.Component
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairedDevicesPanelTest {
  @Test
  fun removeButtonEnabledStatus() = runTestWithFixture {
    val handle2 = createHandle("2")
    deviceHandles.send(listOf(mainHandle, handle2))
    yield()
    pairedDevices.send(mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTING))))
    yield()

    assertThat(panel.removeButton.isEnabled).isFalse()

    pairingTable.selection.selectNextRow()
    yield()

    assertThat(panel.removeButton.isEnabled).isTrue()
  }

  @Test
  fun deviceChangesPairingState() = runTestWithFixture {
    val handle2 = createHandle("2")
    deviceHandles.send(listOf(mainHandle, handle2))
    yield()
    pairedDevices.send(emptyMap())
    yield()

    assertThat(panel.pairingsTable.values).isEmpty()

    pairedDevices.send(mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTING))))
    yield()

    assertThat(panel.pairingsTable.values).hasSize(1)
    assertThat(panel.pairingsTable.values).containsExactly(handle2.pairedDeviceData(WearPairingManager.PairingState.CONNECTING))

    pairedDevices.send(mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))))
    yield()

    assertThat(panel.pairingsTable.values).containsExactly(handle2.pairedDeviceData(WearPairingManager.PairingState.CONNECTED))

    pairedDevices.send(emptyMap())
    yield()
    assertThat(panel.pairingsTable.values).isEmpty()
  }

  @Test
  fun deviceGoesAway() = runTestWithFixture {
    val handle2 = createHandle("2")
    val handle3 = createHandle("3")
    deviceHandles.send(listOf(mainHandle, handle2, handle3))
    yield()
    pairedDevices.send(mapOf(mainHandle.name to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))))
    yield()

    assertThat(pairingTable.values).hasSize(1)

    deviceHandles.send(listOf(mainHandle, handle3))
    yield()

    assertThat(pairingTable.values).isEmpty()
  }

  @Test
  fun pairingGoesAway() = runTestWithFixture {
    val handle2 = createHandle("2")
    val handle3 = createHandle("3")
    deviceHandles.send(listOf(mainHandle, handle2, handle3))
    yield()
    pairedDevices.send(
      mapOf(
        mainHandle.name to
          listOf(
            handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED),
            handle3.pairingStatus(WearPairingManager.PairingState.CONNECTED),
          )
      )
    )
    yield()

    assertThat(pairingTable.values).hasSize(2)

    pairedDevices.send(mapOf(mainHandle.name to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))))
    yield()

    assertThat(pairingTable.values).hasSize(1)

    pairedDevices.send(emptyMap())
    yield()

    assertThat(pairingTable.values).isEmpty()
  }

  @Test
  fun testRemovePresentationState() = runTestWithFixture {
    val handle2 = createHandle("2")
    deviceHandles.send(listOf(mainHandle, handle2))
    yield()
    pairedDevices.send(mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))))
    yield()

    assertThat(panel.removeButton.isEnabled).isFalse()

    pairingTable.selection.selectNextRow()
    yield()

    assertThat(panel.removeButton.isEnabled).isTrue()

    val presentation = pairingDelegate.getRemovePresentation(mainHandle, handle2)
    assertThat(presentation.isEnabled).isTrue()
    assertThat(presentation.confirmationTitle).isEqualTo("Title")
    assertThat(presentation.confirmationMessage).isEqualTo("Message")
  }

  @Test
  fun testMultiDelegateAddButtonPopup() = runTest {
    withContext(Dispatchers.EDT) {
      val delegate1 = TestPairingDelegate()
      val delegate2 = TestGlassesPairingDelegate()
      val testScope = createChildScope()
      val handle = FakeDeviceHandle(testScope, "phone1", deviceType = DeviceType.HANDHELD)
      val panel = PairedDevicesPanel.create(listOf(delegate1, delegate2), testScope, Dispatchers.EDT, handle, emptyFlow())
      assertThat(panel.addButton.isEnabled).isTrue()
      testScope.cancel()
    }
  }

  private fun runTestWithFixture(block: suspend Fixture.() -> Unit) = runTest {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(this@runTest)
      fixture.block()
      fixture.scope.cancel()
    }
  }

  private class Fixture(testScope: TestScope, isGlasses: Boolean = false) {
    val deviceHandles = Channel<List<DeviceHandle>>(UNLIMITED)
    val pairedDevices = Channel<Map<String, List<PairingStatus>>>(UNLIMITED)

    // UnconfinedTestDispatcher is extremely useful here to cause actions to run to completion.
    val dispatcher = UnconfinedTestDispatcher(testScope.testScheduler)

    val scope = testScope.createChildScope(context = dispatcher)

    val sharedPairedDevicesFlow = pairedDevices.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyMap())
    val pairingDelegate =
      if (isGlasses) TestGlassesPairingDelegate(sharedPairedDevicesFlow) else TestPairingDelegate(sharedPairedDevicesFlow)

    val mainHandle =
      if (isGlasses) {
        createGlassesPhoneHandle("phone1")
      } else {
        FakeDeviceHandle(scope, "1")
      }

    val panel =
      PairedDevicesPanel.create(
        listOf(pairingDelegate),
        scope,
        Dispatchers.EDT,
        mainHandle,
        deviceHandles.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyList()),
      )
    val pairingTable = panel.pairingsTable

    fun createHandle(name: String) = FakeDeviceHandle(scope, name)

    fun createGlassesPhoneHandle(name: String) = FakeDeviceHandle(scope, name, deviceType = DeviceType.HANDHELD, wearPairingId = null)

    fun createGlassesHandle(name: String, pairedPhoneId: String) =
      FakeDeviceHandle(
        scope,
        name,
        deviceType = DeviceType.AI_GLASSES,
        wearPairingId = null,
        pairedPhoneId = DeviceId("Fake", false, pairedPhoneId),
      )
  }

  @Test
  fun glassesTracking() = runTest {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(this@runTest, isGlasses = true)
      val glassesHandle = fixture.createGlassesHandle("glasses1", "phone1")

      fixture.deviceHandles.send(listOf(fixture.mainHandle, glassesHandle))
      yield()
      fixture.pairedDevices.send(emptyMap())
      yield()

      assertThat(fixture.panel.pairingsTable.values).isEmpty()

      fixture.pairedDevices.send(
        mapOf("Fake::phone1" to listOf(PairingStatus("Fake::glasses1", "Glasses", WearPairingManager.PairingState.CONNECTED)))
      )
      yield()

      assertThat(fixture.panel.pairingsTable.values).hasSize(1)
      assertThat(fixture.panel.pairingsTable.values)
        .containsExactly(glassesHandle.pairedDeviceData(WearPairingManager.PairingState.CONNECTED))

      fixture.scope.cancel()
    }
  }

  @Test
  fun testIndirectPairedGlassesDisplay() = runTest {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(this@runTest, isGlasses = true)
      val glassesHandle = fixture.createGlassesHandle("glasses1", "phone1")

      fixture.deviceHandles.send(listOf(fixture.mainHandle, glassesHandle))
      yield()

      val indirectPairingStatus = PairingStatus(glassesHandle.id.toString(), "glasses1", WearPairingManager.PairingState.CONNECTED)
      fixture.pairedDevices.send(mapOf(fixture.mainHandle.id.toString() to listOf(indirectPairingStatus)))
      yield()

      assertThat(fixture.panel.pairingsTable.values).hasSize(1)
      assertThat(fixture.panel.pairingsTable.values)
        .containsExactly(glassesHandle.pairedDeviceData(WearPairingManager.PairingState.CONNECTED))

      fixture.scope.cancel()
    }
  }

  @Test
  fun pairedDevicesPanel_receivesEmissionsWhenWearListIsEmpty() = runTest {
    withContext(Dispatchers.EDT) {
      val dispatcher = UnconfinedTestDispatcher(testScheduler)
      val testScope = createChildScope(context = dispatcher)
      val wearDelegate = TestPairingDelegate(flowOf(emptyMap()))
      val glassesHandle = FakeDeviceHandle(testScope, "glasses1", deviceType = DeviceType.AI_GLASSES, wearPairingId = null)
      val mainHandle = FakeDeviceHandle(testScope, "phone1", deviceType = DeviceType.HANDHELD, wearPairingId = null)
      val glassesStatusMap =
        mapOf(
          mainHandle.id.toString() to
            listOf(PairingStatus(glassesHandle.id.toString(), "glasses1", WearPairingManager.PairingState.CONNECTED))
        )
      val glassesDelegate = TestGlassesPairingDelegate(flowOf(glassesStatusMap))

      val devicesFlow = MutableStateFlow(listOf(mainHandle, glassesHandle))
      val panel = PairedDevicesPanel.create(listOf(wearDelegate, glassesDelegate), testScope, Dispatchers.EDT, mainHandle, devicesFlow)

      yield()
      assertThat(panel.pairingsTable.values).hasSize(1)
      assertThat(panel.pairingsTable.values).containsExactly(glassesHandle.pairedDeviceData(WearPairingManager.PairingState.CONNECTED))

      testScope.cancel()
    }
  }

  class FakeDeviceHandle(
    override val scope: CoroutineScope,
    val name: String,
    deviceType: DeviceType? = null,
    wearPairingId: String? = name,
    pairedPhoneId: DeviceId? = null,
    pairedGlassesInfos: List<PairedGlassesInfo> = emptyList(),
  ) : DeviceHandle {
    override val id = DeviceId("Fake", false, name)
    override val stateFlow =
      MutableStateFlow<DeviceState>(
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            this.deviceType = deviceType
            this.wearPairingId = wearPairingId
            this.pairedPhoneId = pairedPhoneId
            this.pairedGlassesInfos = pairedGlassesInfos
            model = name
            icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          }
        )
      )
  }

  private fun FakeDeviceHandle.pairingStatus(state: WearPairingManager.PairingState) = PairingStatus(name, name, state)

  private fun FakeDeviceHandle.pairedDeviceData(state: WearPairingManager.PairingState) =
    PairedDeviceData(this, name, stateFlow.value.properties.icon, null, state)

  open class TestPairingDelegate(val pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>> = emptyFlow()) : PairingDelegate {
    override val addMenuItemTitle: String = "Test Pair Device"

    override fun getPairingId(handle: DeviceHandle): String? = handle.state.properties.wearPairingId

    override fun isDelegateForDevice(handle: DeviceHandle, pairedDevice: DeviceHandle): Boolean =
      pairedDevice.state.properties.wearPairingId != null ||
        pairedDevice.state.properties.deviceType == com.android.sdklib.deviceprovisioner.DeviceType.WEAR

    override fun isPairDeviceWizardSupported(handle: DeviceHandle): Boolean = true

    override suspend fun showPairDeviceWizard(parent: Component, handle: DeviceHandle) {}

    override fun getRemovePresentation(handle: DeviceHandle, pairedDevice: DeviceHandle): RemovePresentation =
      RemovePresentation(isEnabled = true, confirmationTitle = "Title", confirmationMessage = "Message")

    override suspend fun removeDevice(parent: Component?, handle: DeviceHandle, pairedDevice: DeviceHandle) {}

    override fun pairedDevicesFlow(handle: DeviceHandle, devicesFlow: Flow<List<DeviceHandle>>): Flow<Map<String, List<PairingStatus>>> =
      pairedDevicesFlow
  }

  class TestGlassesPairingDelegate(pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>> = emptyFlow()) :
    TestPairingDelegate(pairedDevicesFlow) {
    override val addMenuItemTitle: String = "Pair AI Glasses..."

    override fun getPairingId(handle: DeviceHandle): String? = handle.id.toString()

    override fun isDelegateForDevice(handle: DeviceHandle, pairedDevice: DeviceHandle): Boolean =
      pairedDevice.state.properties.deviceType == DeviceType.AI_GLASSES || pairedDevice.state.properties.pairedGlassesInfos.isNotEmpty()
  }
}
