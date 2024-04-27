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
package com.android.tools.idea.run.deployment.selector

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.BootSnapshotAction
import com.android.sdklib.deviceprovisioner.ColdBootAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.EditTemplateAction
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshot
import com.android.sdklib.deviceprovisioner.Snapshot
import com.android.sdklib.deviceprovisioner.TemplateActivationAction
import com.android.testutils.MockitoKt
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.DeviceHandleAndroidDevice
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.run.DeviceTemplateAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import kotlin.coroutines.EmptyCoroutineContext

internal fun handleId(id: String) = DeviceId("Test", false, id)

internal fun templateId(id: String) = DeviceId("Test", true, id)

internal class FakeDeviceHandle(
  override val scope: CoroutineScope,
  override val sourceTemplate: DeviceTemplate? = null,
  override val id: DeviceId,
  initialProperties: DeviceProperties =
    DeviceProperties.buildForTest {
      model = id.identifier
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
    },
  hasSnapshots: Boolean = false,
) : DeviceHandle {
  override val stateFlow =
    MutableStateFlow<DeviceState>(DeviceState.Disconnected(initialProperties))

  /**
   * Updates the state of the device to Connected, using a mock ConnectedDevice.
   *
   * Does not update the state of any actions.
   */
  fun connectToMockDevice(): ConnectedDevice =
    MockitoKt.mock<ConnectedDevice>().also { mockDevice ->
      MockitoKt.whenever(mockDevice.deviceInfoFlow)
        .thenReturn(MutableStateFlow(DeviceInfo("SN1234", com.android.adblib.DeviceState.ONLINE)))
      stateFlow.update { DeviceState.Connected(it.properties, mockDevice) }
    }

  override val activationAction =
    object : ActivationAction {
      override suspend fun activate() {}

      override val presentation =
        MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
    }

  override val coldBootAction =
    object : ColdBootAction {
      override suspend fun activate() {}

      override val presentation =
        MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
    }

  override val bootSnapshotAction: BootSnapshotAction? =
    if (hasSnapshots)
      object : BootSnapshotAction {
        override suspend fun activate(snapshot: Snapshot) {}

        override suspend fun snapshots(): List<Snapshot> =
          listOf(LocalEmulatorSnapshot("snap-1", Path.of("/tmp/snap-1")))

        override val presentation =
          MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
      }
    else null
}

internal class FakeDeviceTemplate(
  override val id: DeviceId,
  override val properties: DeviceProperties =
    DeviceProperties.buildForTest {
      model = id.identifier
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
    },
) : DeviceTemplate {
  override val activationAction =
    object : TemplateActivationAction {
      override suspend fun activate(duration: Duration?): DeviceHandle {
        throw UnsupportedOperationException()
      }

      override val durationUsed: Boolean = false
      override val presentation =
        MutableStateFlow(StudioDefaultDeviceActionPresentation.fromContext())
    }
  override val editAction: EditTemplateAction? = null
}

internal fun createDevice(
  name: String,
  scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
  disambiguator: String? = null,
  sourceTemplate: DeploymentTargetDevice? = null,
  connectionTime: Instant? = null,
  launchCompatibility: LaunchCompatibility = LaunchCompatibility.YES,
  hasSnapshots: Boolean = false,
): DeploymentTargetDevice {
  val handle =
    FakeDeviceHandle(
      scope.createChildScope(),
      (sourceTemplate?.androidDevice as? DeviceTemplateAndroidDevice)?.deviceTemplate,
      DeviceId("Test", false, name),
      DeviceProperties.buildForTest {
        model = name
        this.disambiguator = disambiguator
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }
    )
  val device =
    DeploymentTargetDevice(
      DeviceHandleAndroidDevice(
        MockitoKt.mock<DeviceProvisionerAndroidDevice.DdmlibDeviceLookup>(),
        handle,
        handle.state
      ),
      connectionTime,
      if (hasSnapshots) listOf(LocalEmulatorSnapshot("snap-1", Paths.get("/tmp/snap")))
      else emptyList(),
      launchCompatibility
    )
  return device
}

internal fun createTemplate(
  id: String,
  scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
  connectionTime: Instant? = null,
  launchCompatibility: LaunchCompatibility = LaunchCompatibility.YES,
): DeploymentTargetDevice {
  val handle = FakeDeviceTemplate(DeviceId("Test", true, id))
  val device =
    DeploymentTargetDevice(
      DeviceTemplateAndroidDevice(
        scope,
        MockitoKt.mock<DeviceProvisionerAndroidDevice.DdmlibDeviceLookup>(),
        handle
      ),
      connectionTime,
      emptyList(),
      launchCompatibility,
    )
  return device
}

internal fun actionEvent(dataContext: DataContext, place: String = "") =
  AnActionEvent(null, dataContext, place, Presentation(), ActionManager.getInstance(), 0)

internal fun dataContext(project: Project? = null): DataContext = DataContext {
  when {
    CommonDataKeys.PROJECT.`is`(it) -> project
    else -> null
  }
}

internal fun RunManager.createTestConfig() =
  createConfiguration("config", AndroidRunConfigurationType::class.java).also {
    addConfiguration(it)
    selectedConfiguration = it
  }
