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

import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.TemplateActivationAction
import com.android.tools.adtui.categorytable.RowKey
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerPanelTest {

  @get:Rule val projectRule = ProjectRule()

  /**
   * When a template is activated, the resulting handle should be just above the (hidden) template,
   * and selected if the template was.
   */
  @Test
  fun activateTemplate() = runTestWithFixture {
    val pixel5Template = createTemplate("Pixel 5")
    val pixel5Handle = createHandle("Pixel 5", pixel5Template)
    val pixel5Emulator = createHandle("Pixel 5")
    val pixel6 = createHandle("Pixel 6")

    deviceHandles.send(listOf(pixel5Emulator, pixel6))
    deviceTemplates.send(listOf(pixel5Template))

    deviceTable.selection.selectRow(RowKey.ValueRowKey(pixel5Template))

    assertThat(deviceTable.values).hasSize(3)
    val originalValues = deviceTable.values.map { it.key() }

    deviceHandles.send(listOf(pixel5Emulator, pixel6, pixel5Handle))

    val valuesAfterActivation =
      originalValues.toMutableList().apply { add(indexOf(pixel5Template), pixel5Handle) }
    assertThat(deviceTable.values.map { it.key() })
      .containsExactlyElementsIn(valuesAfterActivation)
      .inOrder()

    assertThat(deviceTable.selection.selectedKeys())
      .containsExactly(RowKey.ValueRowKey<DeviceRowData>(pixel5Handle))
  }

  private fun runTestWithFixture(block: suspend Fixture.() -> Unit) = runTest {
    val fixture = Fixture(projectRule.project, this)
    fixture.block()
    fixture.scope.cancel()
  }

  private class Fixture(project: Project, testScope: TestScope) {
    val deviceHandles = Channel<List<DeviceHandle>>(Channel.UNLIMITED)
    val deviceTemplates = Channel<List<DeviceTemplate>>(Channel.UNLIMITED)
    val pairedDevices = Channel<Map<String, List<PairingStatus>>>(Channel.UNLIMITED)

    // UnconfinedTestDispatcher is extremely useful here to cause actions to run to completion.
    val dispatcher = UnconfinedTestDispatcher(testScope.testScheduler)

    val scope = testScope.createChildScope(context = dispatcher)

    val panel =
      DeviceManagerPanel(
        project,
        scope,
        dispatcher,
        deviceHandles.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyList()),
        deviceTemplates.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyList()),
        emptyList(),
        emptyList(),
        pairedDevices.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyMap()),
      )
    val deviceTable = panel.deviceTable

    fun createHandle(name: String, sourceTemplate: DeviceTemplate? = null) =
      FakeDeviceHandle(scope, name, sourceTemplate)
    fun createTemplate(name: String) = FakeDeviceTemplate(name)
  }

  private class FakeDeviceHandle(
    override val scope: CoroutineScope,
    val name: String,
    override val sourceTemplate: DeviceTemplate?,
  ) : DeviceHandle {
    override val stateFlow =
      MutableStateFlow<DeviceState>(
        DeviceState.Disconnected(DeviceProperties.build { model = name })
      )
  }

  private class FakeDeviceTemplate(
    val name: String,
  ) : DeviceTemplate {
    override val properties = DeviceProperties.build { model = name }
    override val activationAction =
      object : TemplateActivationAction {
        override suspend fun activate(duration: Duration?) = throw UnsupportedOperationException()
        override val durationUsed = false
        override val presentation =
          MutableStateFlow(DeviceAction.Presentation("", StudioIcons.Avd.RUN, true))
      }
    override val editAction = null
  }
}
