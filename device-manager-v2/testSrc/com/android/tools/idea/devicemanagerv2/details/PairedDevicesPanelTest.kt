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
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairedDevicesPanelTest {
  @Test
  fun removeButtonEnabledStatus() = runTestWithFixture {
    val handle2 = createHandle("2")
    deviceHandles.send(listOf(mainHandle, handle2))
    pairedDevices.send(
      mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTING)))
    )

    assertThat(panel.removeButton.isEnabled).isFalse()

    pairingTable.selection.selectNextRow()

    assertThat(panel.removeButton.isEnabled).isTrue()
  }

  @Test
  fun deviceChangesPairingState() = runTestWithFixture {
    val handle2 = createHandle("2")
    deviceHandles.send(listOf(mainHandle, handle2))
    pairedDevices.send(emptyMap())

    assertThat(panel.pairingsTable.values).isEmpty()

    pairedDevices.send(
      mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTING)))
    )

    assertThat(panel.pairingsTable.values).hasSize(1)
    assertThat(panel.pairingsTable.values)
      .containsExactly(handle2.pairedDeviceData(WearPairingManager.PairingState.CONNECTING))

    pairedDevices.send(
      mapOf("1" to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED)))
    )

    assertThat(panel.pairingsTable.values)
      .containsExactly(handle2.pairedDeviceData(WearPairingManager.PairingState.CONNECTED))

    pairedDevices.send(emptyMap())
    assertThat(panel.pairingsTable.values).isEmpty()
  }

  @Test
  fun deviceGoesAway() = runTestWithFixture {
    val handle2 = createHandle("2")
    val handle3 = createHandle("3")
    deviceHandles.send(listOf(mainHandle, handle2, handle3))
    pairedDevices.send(
      mapOf(
        mainHandle.name to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))
      )
    )

    assertThat(pairingTable.values).hasSize(1)

    deviceHandles.send(listOf(mainHandle, handle3))

    assertThat(pairingTable.values).isEmpty()
  }

  @Test
  fun pairingGoesAway() = runTestWithFixture {
    val handle2 = createHandle("2")
    val handle3 = createHandle("3")
    deviceHandles.send(listOf(mainHandle, handle2, handle3))
    pairedDevices.send(
      mapOf(
        mainHandle.name to
          listOf(
            handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED),
            handle3.pairingStatus(WearPairingManager.PairingState.CONNECTED)
          )
      )
    )

    assertThat(pairingTable.values).hasSize(2)

    pairedDevices.send(
      mapOf(
        mainHandle.name to listOf(handle2.pairingStatus(WearPairingManager.PairingState.CONNECTED))
      )
    )

    assertThat(pairingTable.values).hasSize(1)

    pairedDevices.send(emptyMap())

    assertThat(pairingTable.values).isEmpty()
  }

  private fun runTestWithFixture(block: suspend Fixture.() -> Unit) = runTest {
    withContext(uiThread) {
      val fixture = Fixture(this@runTest)
      fixture.block()
      fixture.scope.cancel()
    }
  }

  private class Fixture(testScope: TestScope) {
    val deviceHandles = Channel<List<DeviceHandle>>(UNLIMITED)
    val pairedDevices = Channel<Map<String, List<PairingStatus>>>(UNLIMITED)

    // UnconfinedTestDispatcher is extremely useful here to cause actions to run to completion.
    val dispatcher = UnconfinedTestDispatcher(testScope.testScheduler)

    val scope = testScope.createChildScope(context = dispatcher)
    val mainHandle = FakeDeviceHandle(scope, "1")

    val panel =
      PairedDevicesPanel.create(
        TestPairingManager(),
        scope,
        dispatcher,
        mainHandle,
        deviceHandles.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyList()),
        pairedDevices.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyMap()),
      )
    val pairingTable = panel.pairingsTable

    fun createHandle(name: String) = FakeDeviceHandle(scope, name)
  }

  class FakeDeviceHandle(
    override val scope: CoroutineScope,
    val name: String,
  ) : DeviceHandle {
    override val id = DeviceId("Fake", false, name)
    override val stateFlow =
      MutableStateFlow<DeviceState>(
        DeviceState.Disconnected(
          DeviceProperties.buildForTest {
            wearPairingId = name
            model = name
            icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          }
        )
      )
  }

  private fun FakeDeviceHandle.pairingStatus(state: WearPairingManager.PairingState) =
    PairingStatus(name, name, state)

  private fun FakeDeviceHandle.pairedDeviceData(state: WearPairingManager.PairingState) =
    PairedDeviceData(this, name, null, null, state)

  open class TestPairingManager : PairedDevicesPanel.PairingManager {
    override fun showPairDeviceWizard(pairingId: String) {}

    override suspend fun removeDevice(phonePairingId: String, wearPairingId: String) {}
  }
}
