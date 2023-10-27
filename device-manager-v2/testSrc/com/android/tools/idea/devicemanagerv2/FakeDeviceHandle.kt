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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeviceActionDisabledException
import com.android.sdklib.deviceprovisioner.DeviceActionException
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.RepairDeviceAction
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope

internal class FakeDeviceHandle(
  override val scope: CoroutineScope,
  override val sourceTemplate: DeviceTemplate? = null,
  initialProperties: DeviceProperties =
    DeviceProperties.buildForTest { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
) : DeviceHandle {
  override val id = DeviceId("Fake", false, initialProperties.title)
  override val stateFlow =
    MutableStateFlow<DeviceState>(DeviceState.Disconnected(initialProperties))
  override val activationAction = FakeActivationAction()
  override val deactivationAction = FakeDeactivationAction()
  override val repairDeviceAction = FakeRepairDeviceAction()
  override val showAction = FakeShowAction()
  override val duplicateAction = FakeDuplicateAction()
  override val wipeDataAction = FakeWipeDataAction()
  override val deleteAction = FakeDeleteAction()
  override val coldBootAction = FakeColdBootAction()

  /**
   * Updates the state of the device to Connected, using a mock ConnectedDevice.
   *
   * Does not update the state of any actions.
   */
  fun connectToMockDevice(): ConnectedDevice =
    mock<ConnectedDevice>().also { mockDevice ->
      whenever(mockDevice.deviceInfoFlow)
        .thenReturn(MutableStateFlow(DeviceInfo("SN1234", com.android.adblib.DeviceState.ONLINE)))
      stateFlow.update { DeviceState.Connected(it.properties, mockDevice) }
    }

  inner class FakeActivationAction : ActivationAction {
    var invoked = 0
    var exception: DeviceActionException? = null

    override suspend fun activate() {
      if (presentation.value.enabled) {
        exception?.let { throw it }
        invoked++
      } else throw DeviceActionDisabledException(this)
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeColdBootAction : com.android.sdklib.deviceprovisioner.ColdBootAction {
    var invoked = 0

    override suspend fun activate() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeDeactivationAction : DeactivationAction {
    var invoked = 0

    override suspend fun deactivate() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeRepairDeviceAction : RepairDeviceAction {
    var invoked = 0
    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())

    override suspend fun repair() {
      invoked++
    }
  }

  inner class FakeShowAction : com.android.sdklib.deviceprovisioner.ShowAction {
    var invoked = 0

    override suspend fun show() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeWipeDataAction : com.android.sdklib.deviceprovisioner.WipeDataAction {
    var invoked = 0

    override suspend fun wipeData() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeDeleteAction : com.android.sdklib.deviceprovisioner.DeleteAction {
    var invoked = 0

    override suspend fun delete() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }

  inner class FakeDuplicateAction : com.android.sdklib.deviceprovisioner.DuplicateAction {
    var invoked = 0

    override suspend fun duplicate() {
      invoked++
    }

    override val presentation =
      MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
  }
}

internal class SingleFakeDeviceFixture(testScope: TestScope) {
  val handleScope = testScope.createChildScope()
  val handle = FakeDeviceHandle(handleScope)
  val actionEvent
    get() =
      actionEvent(
        dataContext(device = handle, deviceRowData = DeviceRowData.create(handle, emptyList()))
      )
}

internal suspend fun TestScope.runWithSingleFakeDevice(
  block: suspend SingleFakeDeviceFixture.() -> Unit
) {
  val fixture = SingleFakeDeviceFixture(this)
  fixture.block()
  fixture.handleScope.cancel()
}

internal fun UsageTrackerRule.deviceManagerEvents(): List<AndroidStudioEvent> =
  usages.mapNotNull {
    it.studioEvent.takeIf { it.kind == AndroidStudioEvent.EventKind.DEVICE_MANAGER }
  }

fun UsageTrackerRule.deviceManagerEventKinds() =
  usages.mapNotNull {
    it.studioEvent.deviceManagerEvent.kind.takeIf { it != DeviceManagerEvent.EventKind.UNSPECIFIED }
  }
