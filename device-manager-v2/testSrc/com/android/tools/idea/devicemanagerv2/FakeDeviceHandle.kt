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

import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.RepairDeviceAction
import com.android.sdklib.deviceprovisioner.TestDefaultDeviceActionPresentation
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class FakeDeviceHandle(
  override val scope: CoroutineScope,
  override val sourceTemplate: DeviceTemplate? = null,
  initialProperties: DeviceProperties =
    DeviceProperties.build { icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE },
) : DeviceHandle {
  override val stateFlow =
    MutableStateFlow<DeviceState>(DeviceState.Disconnected(initialProperties))
  override val activationAction = FakeActivationAction()
  override val deactivationAction = FakeDeactivationAction()
  override val repairDeviceAction = FakeRepairDeviceAction()

  var active = false

  inner class FakeActivationAction : ActivationAction {
    var invoked = 0
    override suspend fun activate() {
      invoked++
      active = true
      presentation.update { it.copy(enabled = false) }
      deactivationAction.presentation.update { it.copy(enabled = true) }
    }

    override val presentation =
      MutableStateFlow(
        TestDefaultDeviceActionPresentation.fromContext().copy(icon = StudioIcons.Avd.RUN)
      )
  }

  inner class FakeDeactivationAction : DeactivationAction {
    var invoked = 0
    override suspend fun deactivate() {
      invoked++
      active = false
      presentation.update { it.copy(enabled = false) }
      activationAction.presentation.update { it.copy(enabled = true) }
    }

    override val presentation =
      MutableStateFlow(
        TestDefaultDeviceActionPresentation.fromContext()
          .copy(icon = StudioIcons.Avd.STOP, enabled = false)
      )
  }

  inner class FakeRepairDeviceAction : RepairDeviceAction {
    var invoked = 0
    override val presentation = MutableStateFlow(TestDefaultDeviceActionPresentation.fromContext())

    override suspend fun repair() {
      invoked++
    }
  }
}
