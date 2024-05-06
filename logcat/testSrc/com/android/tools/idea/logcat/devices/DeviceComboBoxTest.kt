/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.devices

import com.android.testutils.MockitoKt.whenever
import com.android.testutils.file.createInMemoryFileSystem
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.DeviceItem
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.FileItem
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.spy
import java.nio.file.Path
import javax.swing.JLabel
import kotlin.io.path.writeText

/** Tests for [DeviceComboBox] */
@Suppress("OPT_IN_USAGE") // runTest is experimental
class DeviceComboBoxTest {
  private val projectRule = ProjectRule()
  private val deviceTracker = FakeDeviceComboBoxDeviceTracker()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ProjectServiceRule(
        projectRule,
        DeviceComboBoxDeviceTrackerFactory::class.java,
        DeviceComboBoxDeviceTrackerFactory { deviceTracker }
      ),
    )

  private val selectionEvents = mutableListOf<Any?>()

  private val device1 = Device.createPhysical("device1", false, "11", 30, "Google", "Pixel 2")
  private val device2 = Device.createPhysical("device2", false, "11", 30, "Google", "Pixel 2")
  private val emulator = Device.createEmulator("emulator-5555", false, "11", 30, "AVD")

  @Test
  fun noDevice_noSelection(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)

      val selectedItems = async { deviceComboBox.trackSelected().toList() }
      deviceTracker.close()

      assertThat(selectedItems.await()).isEmpty()
      assertThat(selectionEvents).isEmpty()
    }

  @Test
  fun noInitialDevice_selectsFirstDevice(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device1),
          Added(device2),
        )
        advanceUntilIdle()
      }

      assertThat(selectionEvents).containsExactly(DeviceItem(device1))
      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          DeviceItem(device1),
          DeviceItem(device2),
        )
        .inOrder()
    }

  @Test
  fun withInitialDevice_selectsInitialDevice(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox =
        deviceComboBox(initialItem = DeviceItem(device2), selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device1),
          Added(device2),
        )
        advanceUntilIdle()
      }

      assertThat(selectionEvents).containsExactly(DeviceItem(device2))
      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          DeviceItem(device1),
          DeviceItem(device2),
        )
        .inOrder()
    }

  @Test
  fun withInitialDevice_selectsInitialFile(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val fileSystem = createInMemoryFileSystem()
      val path = fileSystem.getPath("file.logcat").apply { writeText("") }

      val deviceComboBox =
        deviceComboBox(initialItem = FileItem(path), selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device1),
          Added(device2),
        )
        advanceUntilIdle()
      }

      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          FileItem(path),
          DeviceItem(device1),
          DeviceItem(device2),
        )
        .inOrder()
    }

  @Test
  fun selectedDeviceStateChanges_selectsDevice(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device2.online()),
          StateChanged(device2.offline()),
        )
        advanceUntilIdle()
      }

      assertThat(selectionEvents)
        .containsExactly(
          DeviceItem(device2.online()),
          DeviceItem(device2.offline()),
        )
      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          DeviceItem(device2.offline()),
        )
        .inOrder()
    }

  @Test
  fun unselectedDeviceStateChanges_doesNotSelect(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device1),
          Added(device2.online()),
          StateChanged(device2.offline()),
        )
        advanceUntilIdle()
      }

      assertThat(selectionEvents).containsExactly(DeviceItem(device1))
      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          DeviceItem(device1),
          DeviceItem(device2.offline()),
        )
        .inOrder()
    }

  @Test
  fun userSelection_sendsToFlow(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }

      deviceTracker.use {
        it.sendEvents(
          Added(device1),
          Added(device2),
        )
        advanceUntilIdle()
        deviceComboBox.selectedItem = DeviceItem(device2)
        advanceUntilIdle()
      }

      assertThat(selectedItems.await())
        .containsExactly(
          DeviceItem(device1),
          DeviceItem(device2),
        )
    }

  @Test
  fun renderer_physicalDevice_offline() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(DeviceItem(device1.offline()), false))
      .isEqualTo("Google Pixel 2 Android 11, API 30 [OFFLINE] [ ]")
    assertThat(deviceComboBox.getRenderedText(DeviceItem(device1.offline()), true))
      .isEqualTo("Google Pixel 2 Android 11, API 30 [OFFLINE] [x]")
  }

  @Test
  fun renderer_physicalDevice_online() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(DeviceItem(device1.online()), false))
      .isEqualTo("Google Pixel 2 (device1) Android 11, API 30 [ ]")
    assertThat(deviceComboBox.getRenderedText(DeviceItem(device1.online()), true))
      .isEqualTo("Google Pixel 2 (device1) Android 11, API 30 [ ]")
  }

  @Test
  fun renderer_emulator_offline() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(DeviceItem(emulator.offline()), false))
      .isEqualTo("AVD Android 11, API 30 [OFFLINE] [ ]")
    assertThat(deviceComboBox.getRenderedText(DeviceItem(emulator.offline()), true))
      .isEqualTo("AVD Android 11, API 30 [OFFLINE] [x]")
  }

  @Test
  fun renderer_emulator_online() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(DeviceItem(emulator.online()), false))
      .isEqualTo("AVD (emulator-5555) Android 11, API 30 [ ]")
    assertThat(deviceComboBox.getRenderedText(DeviceItem(emulator.online()), true))
      .isEqualTo("AVD (emulator-5555) Android 11, API 30 [ ]")
  }

  @Test
  fun renderer_file() {
    val deviceComboBox = deviceComboBox()

    assertThat(deviceComboBox.getRenderedText(FileItem(Path.of("foo")), false)).isEqualTo("foo [ ]")
    assertThat(deviceComboBox.getRenderedText(FileItem(Path.of("foo")), true)).isEqualTo("foo [x]")
  }

  @Test
  fun addOrSelectFile(): Unit =
    runTest(dispatchTimeoutMs = 5_000) {
      val fileSystem = createInMemoryFileSystem()
      val path = fileSystem.getPath("file.logcat").apply { writeText("") }
      val deviceComboBox = deviceComboBox(selectionEvents = selectionEvents)
      val selectedItems = async { deviceComboBox.trackSelected().toList() }
      advanceUntilIdle()

      deviceComboBox.addOrSelectFile(path)
      advanceUntilIdle()
      deviceTracker.close()

      assertThat(selectionEvents).containsExactly(FileItem(path))
      assertThat(selectedItems.await()).isEqualTo(selectionEvents)
      assertThat(deviceComboBox.getItems())
        .containsExactly(
          FileItem(path),
        )
        .inOrder()
    }

  private fun deviceComboBox(
    initialItem: DeviceComboItem? = null,
    selectionEvents: MutableList<Any?> = mutableListOf(),
  ): DeviceComboBox {
    return DeviceComboBox(projectRule.project, initialItem).also {
      // Replace the model with a spy that records all the calls to setSelectedItem()
      it.model = spy(it.model)
      whenever(it.model.setSelectedItem(any())).thenAnswer { invocation ->
        invocation.callRealMethod()
        selectionEvents.add(invocation.arguments[0])
      }
    }
  }
}

private fun Device.offline() = copy(isOnline = false)

private fun Device.online() = copy(isOnline = true)

private fun DeviceComboBox.getRenderedText(item: DeviceComboItem, isSelected: Boolean): String {
  val walker =
    TreeWalker(renderer.getListCellRendererComponent(JBList(model), item, 0, isSelected, false))
  val deleteLabel = walker.descendants().first { it is JLabel } as JLabel
  val deviceComponent =
    walker.descendants().first { it is SimpleColoredComponent } as SimpleColoredComponent
  val deletable = if (deleteLabel.icon == null) " " else "x"
  return "$deviceComponent [$deletable]"
}

private fun DeviceComboBox.getItems(): List<DeviceComboItem> =
  (model as CollectionComboBoxModel<DeviceComboItem>).items
