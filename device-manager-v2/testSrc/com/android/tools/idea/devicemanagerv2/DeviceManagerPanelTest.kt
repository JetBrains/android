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

import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.utils.createChildScope
import com.android.flags.junit.FlagRule
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.categorytable.CategoryTable
import com.android.tools.adtui.categorytable.ColumnSortOrder
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.adtui.categorytable.RowKey.ValueRowKey
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.EditorNotificationPanel
import icons.StudioIcons
import javax.swing.JPanel
import javax.swing.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagerPanelTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val nestedViewFlagRule = FlagRule(StudioFlags.AI_GLASSES_NESTED_DEVICE_VIEW_ENABLED, true)

  @Test
  fun initialSortOrder() = runTestWithFixture {
    assertThat(deviceTable.columnSorters).containsExactly(ColumnSortOrder(DeviceTableColumns.nameAttribute, SortOrder.ASCENDING))
  }

  /** When a template is activated, the resulting handle should be just above the (hidden) template, and selected if the template was. */
  @Test
  fun activateTemplate() = runTestWithFixture {
    val pixel5Template = createTemplate("Pixel 5")
    val pixel5Handle = createHandle("Pixel 5", pixel5Template)
    val pixel5Emulator = createHandle("Pixel 5 Other")
    val pixel6 = createHandle("Pixel 6")

    deviceHandles.send(listOf(pixel5Emulator, pixel6))
    deviceTemplates.send(listOf(pixel5Template))

    deviceTable.selection.selectRow(ValueRowKey(pixel5Template.id))

    assertThat(deviceTable.values).hasSize(3)
    val originalIds = deviceTable.values.map { it.id }

    deviceHandles.send(listOf(pixel5Emulator, pixel6, pixel5Handle))

    val templateIndex = deviceTable.values.indexOfFirst { it.template == pixel5Template }
    val idsAfterActivation = originalIds.toMutableList().apply { add(templateIndex, pixel5Handle.id) }
    assertThat(deviceTable.values.map { it.id }).containsExactlyElementsIn(idsAfterActivation).inOrder()

    assertThat(deviceTable.selection.selectedKeys()).containsExactly(ValueRowKey<DeviceRowData>(pixel5Handle.id))
  }

  @Test
  fun templateVisibility() = runTestWithFixture {
    val pixel4 = createHandle("Pixel 4")
    val pixel5Template = createTemplate("Pixel 5")
    val pixel5Handle = createHandle("Pixel 5", pixel5Template)
    val pixel6 = createHandle("Pixel 6")

    deviceHandles.send(listOf(pixel4, pixel6))
    deviceTemplates.send(listOf(pixel5Template))

    assertThat(deviceTable.visibleKeys()).containsExactly(pixel4.id, pixel5Template.id, pixel6.id)

    deviceHandles.send(listOf(pixel4, pixel6, pixel5Handle))
    // Send an update to the state to be more realistic
    pixel5Handle.stateFlow.update {
      DeviceState.Disconnected(
        DeviceProperties.buildForTest {
          manufacturer = "Google"
          model = "Pixel 5"
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        }
      )
    }
    assertThat(deviceTable.visibleKeys()).containsExactly(pixel4.id, pixel5Handle.id, pixel6.id)

    deviceHandles.send(listOf(pixel4, pixel6))
    pixel5Handle.scope.cancel()

    assertThat(deviceTable.visibleKeys()).containsExactly(pixel4.id, pixel5Template.id, pixel6.id)
  }

  @Test
  fun detailsTracksSelection() = runTestWithFixture {
    val pixel4 = createHandle("Pixel 4")
    val pixel5 = createHandle("Pixel 5")

    deviceHandles.send(listOf(pixel4, pixel5))

    val pixel4Row = DeviceRowData.create(pixel4, emptyList())
    val pixel5Row = DeviceRowData.create(pixel5, emptyList())

    ViewDetailsAction().actionPerformed(actionEvent(dataContext(panel, deviceRowData = pixel4Row)))

    assertThat(panel.deviceDetailsPanelRow).isEqualTo(pixel4Row)

    deviceTable.selection.selectRow(ValueRowKey(pixel5.id))

    assertThat(panel.deviceDetailsPanelRow).isEqualTo(pixel5Row)

    deviceTable.selection.clear()

    // Shouldn't change anything
    assertThat(panel.deviceDetailsPanelRow).isEqualTo(pixel5Row)
  }

  @Test
  fun detailsClosedWhenHandleNoLongerExists() = runTestWithFixture {
    val pixel4 = createHandle("Pixel 4")
    val pixel5 = createHandle("Pixel 5")

    deviceHandles.send(listOf(pixel4, pixel5))

    val pixel4Row = DeviceRowData.create(pixel4, emptyList())

    ViewDetailsAction().actionPerformed(actionEvent(dataContext(panel, deviceRowData = pixel4Row)))

    assertThat(panel.deviceDetailsPanelRow).isEqualTo(pixel4Row)

    pixel4.scope.cancel()

    assertThat(panel.deviceDetailsPanelRow).isNull()
    assertThat(panel.deviceDetailsPanel).isNull()
  }

  @Test
  fun detailsClosedWhenTemplateNoLongerExists() = runTestWithFixture {
    val pixel4 = createTemplate("Pixel 4")
    val pixel5 = createHandle("Pixel 5")

    deviceHandles.send(listOf(pixel5))
    deviceTemplates.send(listOf(pixel4))

    val pixel4Row = DeviceRowData.create(pixel4)

    ViewDetailsAction().actionPerformed(actionEvent(dataContext(panel, deviceRowData = pixel4Row)))

    assertThat(panel.deviceDetailsPanelRow).isEqualTo(pixel4Row)

    deviceTemplates.send(listOf())

    assertThat(panel.deviceDetailsPanelRow).isNull()
    assertThat(panel.deviceDetailsPanel).isNull()
  }

  @Test
  fun clickRow() = runTestWithFixture {
    val pixel4 = createHandle("Pixel 4")
    val pixel5 = createHandle("Pixel 5")

    deviceHandles.send(listOf(pixel4, pixel5))

    panel.setBounds(0, 0, 800, 400)

    val fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    fakeUi.layout()

    assertThat(deviceTable.selection.selectedKeys()).isEmpty()

    val runButton = checkNotNull(fakeUi.findComponent<IconButton> { it.baseIcon == StudioIcons.Avd.RUN })
    assertThat(runButton.isEnabled).isTrue()

    fakeUi.clickOn(runButton)

    assertThat(deviceTable.selection.selectedKeys()).containsExactly(ValueRowKey<DeviceRowData>(pixel4.id))
  }

  @Test
  fun tracksNotificationBanners() = runTestWithFixture {
    val banner = EditorNotificationPanel().apply { text = "Warnings" }
    notificationBanners.send(listOf(banner))
    panel.setBounds(0, 0, 800, 400)

    val fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    fakeUi.layout()
    assertThat((((panel.components[0]) as JPanel).components[0] as JPanel).components[0]).isEqualTo(banner)

    // Banner component gets removed with notificationBanners flow.
    notificationBanners.send(listOf())
    yieldUntil { panel.findAllDescendants<EditorNotificationPanel>().none() }
  }

  fun <T : Any> CategoryTable<T>.visibleKeys() = values.mapNotNull { primaryKey(it).takeIf { isRowVisibleByKey(it) } }

  private fun runTestWithFixture(block: suspend Fixture.() -> Unit) = runTest {
    withContext(Dispatchers.EDT) {
      val fixture = Fixture(projectRule.project, this@runTest)
      fixture.block()
      fixture.scope.cancel()
    }
  }

  private class Fixture(project: Project, testScope: TestScope) {
    val deviceHandles = Channel<List<DeviceHandle>>(Channel.UNLIMITED)
    val deviceTemplates = Channel<List<DeviceTemplate>>(Channel.UNLIMITED)
    val notificationBanners = Channel<List<EditorNotificationPanel>>(Channel.UNLIMITED)
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
        notificationBanners.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyList()),
        emptyList(),
        emptyList(),
        pairedDevices.consumeAsFlow().stateIn(scope, SharingStarted.Lazily, emptyMap()),
        deviceFilter = { true },
      )
    val deviceTable = panel.deviceTable

    fun createHandle(name: String, sourceTemplate: DeviceTemplate? = null, propertiesBlock: DeviceProperties.Builder.() -> Unit = {}) =
      FakeDeviceHandle(
        scope.createChildScope(isSupervisor = true),
        sourceTemplate,
        DeviceProperties.buildForTest {
          model = name
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          propertiesBlock()
        },
      )

    fun createTemplate(name: String) = FakeDeviceTemplate(name)
  }

  @Test
  fun dataKeysPresent() = runTestWithFixture {
    val handle = createHandle("device")
    deviceTable.addOrUpdateRow(DeviceRowData.create(handle, emptyList()))

    assertThat(DataKey.allKeys().map { it.name }).containsAllOf("DeviceHandle", "DeviceRowData")
  }

  @Test
  fun nesting() = runTestWithFixture {
    val phoneHandle = createHandle("Pixel 6") { deviceType = DeviceType.HANDHELD }
    val glassesHandle =
      createHandle("Glasses A") {
        deviceType = DeviceType.AI_GLASSES
        pairedPhoneId = phoneHandle.id
      }
    val otherPhone = createHandle("Pixel 5") { deviceType = DeviceType.HANDHELD }

    deviceHandles.send(listOf(phoneHandle, glassesHandle, otherPhone))

    panel.setBounds(0, 0, 800, 400)
    val fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    fakeUi.layout()

    // When nested, Glasses A follows its parent Phone (Pixel 6):
    assertThat(deviceTable.values.map { it.name }).containsExactly("Pixel 5", "Pixel 6", "Glasses A").inOrder()
  }

  @Test
  fun nesting_flagDisabled() = runTestWithFixture {
    StudioFlags.AI_GLASSES_NESTED_DEVICE_VIEW_ENABLED.override(false)
    try {
      val phoneHandle = createHandle("Pixel 6") { deviceType = DeviceType.HANDHELD }
      val glassesHandle =
        createHandle("Glasses A") {
          deviceType = DeviceType.AI_GLASSES
          pairedPhoneId = phoneHandle.id
        }
      val otherPhone = createHandle("Pixel 5") { deviceType = DeviceType.HANDHELD }

      deviceHandles.send(listOf(phoneHandle, glassesHandle, otherPhone))

      panel.setBounds(0, 0, 800, 400)
      val fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
      fakeUi.layout()

      // When flat (flag disabled), alphabetical sorting by Name applies flatly:
      assertThat(deviceTable.values.map { it.name }).containsExactly("Glasses A", "Pixel 5", "Pixel 6").inOrder()
    } finally {
      StudioFlags.AI_GLASSES_NESTED_DEVICE_VIEW_ENABLED.clearOverride()
    }
  }

  @Test
  fun nesting_groupedByType() = runTestWithFixture {
    val phoneHandle = createHandle("Pixel 6") { deviceType = DeviceType.HANDHELD }
    val glassesHandle =
      createHandle("Glasses A") {
        deviceType = DeviceType.AI_GLASSES
        pairedPhoneId = phoneHandle.id
      }
    val otherPhone = createHandle("Pixel 5") { deviceType = DeviceType.HANDHELD }

    deviceHandles.send(listOf(phoneHandle, glassesHandle, otherPhone))

    // Group by Type (HandleType)
    deviceTable.addGrouping(DeviceTableColumns.HandleType)

    panel.setBounds(0, 0, 800, 400)
    val fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    fakeUi.layout()

    // Both are Virtual devices, so they belong to the same Category and should nest:
    assertThat(deviceTable.values.map { it.name }).containsExactly("Pixel 5", "Pixel 6", "Glasses A").inOrder()
  }
}
