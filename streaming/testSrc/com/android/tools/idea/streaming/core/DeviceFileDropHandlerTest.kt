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
package com.android.tools.idea.streaming.core

import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.idea.adb.FakeAdbServiceRule
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.android.tools.idea.streaming.emulator.FakeEmulatorRule
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

private const val TEST_DATA_PATH = "tools/adt/idea/streaming/testData/DeviceFileDropHandlerTest"

/**
 * Tests for [DeviceFileDropHandler].
 */
@RunsInEdt
class DeviceFileDropHandlerTest {

  private val projectRule = ProjectRule()
  private val emulatorRule = FakeEmulatorRule()
  private val adbRule = FakeAdbRule()
  private val adbServiceRule = FakeAdbServiceRule(projectRule::project, adbRule)
  @get:Rule
  val ruleChain = RuleChain(projectRule, adbRule, adbServiceRule, emulatorRule, EdtRule())
  @get:Rule
  val tempDirRule = TemporaryDirectoryRule()

  private var nullableEmulator: FakeEmulator? = null

  private var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  private val testRootDisposable
    get() = projectRule.disposable

  @Test
  fun testDragToInstallApp() {
    val apk = TestUtils.resolveWorkspacePath("$TEST_DATA_PATH/test.apk")
    val event = createDragEvent(listOf(apk.toFile()))

    val target = createDropTarget()
    // Simulate drag.
    target.update(event)

    verify(event).isDropPossible = true

    val device = attachDevice()

    // Simulate drop.
    target.drop(event)

    waitForCondition(2, SECONDS) { device.cmdLogs.size >= 3 }
    assertThat(device.cmdLogs).containsExactly("package install-create -t --user current --full -S 675",
                                               "package install-write -S 675 1234 test.apk -", "package install-commit 1234")
  }

  @Test
  fun testDragToPushFiles() {
    val dir = Files.createDirectories(tempDirRule.newPath())
    val file1 = dir.resolve("file1.txt")
    Files.createFile(file1)
    val file2 = dir.resolve("file2.jpg")
    Files.createFile(file2)
    val event = createDragEvent(listOf(file1.toFile(), file2.toFile()))

    val target = createDropTarget()
    // Simulate drag.
    target.update(event)

    verify(event).isDropPossible = true

    val device = attachDevice()

    // Simulate drop.
    target.drop(event)

    waitForCondition(2, SECONDS) {
      device.getFile("/sdcard/Download/${file1.fileName}") != null &&
      device.getFile("/sdcard/Download/${file2.fileName}") != null
    }
    assertThat(device.getFile("/sdcard/Download/${file1.fileName}")?.permission).isEqualTo(420)
    assertThat(device.getFile("/sdcard/Download/${file2.fileName}")?.permission).isEqualTo(420)
  }

  private fun attachDevice() =
      adbRule.attachDevice("emulator-${emulator.serialPort}", "Google", "Pixel 3 XL", "Sweet dessert", "29")

  private fun createDropTarget(): DnDTarget {
    var nullableTarget: DnDTarget? = null
    val mockDndManager = mock<DnDManager>()
    whenever(mockDndManager.registerTarget(any(), any())).then {
      it.apply { nullableTarget = getArgument<DnDTarget>(0) }
    }
    ApplicationManager.getApplication().replaceService(DnDManager::class.java, mockDndManager, testRootDisposable)

    val panel = createWindowPanelForPhone()
    panel.createContent(false)
    Disposer.register(testRootDisposable) { panel.destroyContent() }

    return nullableTarget as DnDTarget
  }

  private fun createDragEvent(files: List<File>): DnDEvent {
    val transferableWrapper = mock<TransferableWrapper>()
    whenever(transferableWrapper.asFileList()).thenReturn(files)
    val event = mock<DnDEvent>()
    whenever(event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)).thenReturn(true)
    whenever(event.attachedObject).thenReturn(transferableWrapper)
    return event
  }

  private fun createWindowPanelForPhone(): EmulatorToolWindowPanel {
    val avdFolder = FakeEmulator.createPhoneAvd(emulatorRule.avdRoot)
    return createWindowPanel(avdFolder)
  }

  private fun createWindowPanel(avdFolder: Path): EmulatorToolWindowPanel {
    emulator = emulatorRule.newEmulator(avdFolder)
    emulator.start()
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(testRootDisposable, projectRule.project, emulatorController)
    Disposer.register(testRootDisposable) {
      emulator.stop()
    }
    panel.zoomToolbarVisible = true
    waitForCondition(5, SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return panel
  }
}
