/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.ddmlib.IDevice
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.PaneEntry.PaneIndex
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.setPortableUiFont
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.run.AppDeploymentListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.override
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

/**
 * Tests for [EmulatorToolWindowManager] and [EmulatorToolWindowFactory].
 */
@RunsInEdt
class EmulatorToolWindowManagerTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableToolWindow: ToolWindow? = null
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  private val project
    get() = projectRule.project

  private var toolWindow: ToolWindow
    get() = nullableToolWindow ?: throw IllegalStateException()
    set(value) { nullableToolWindow = value }

  @Before
  fun setUp() {
    setPortableUiFont()
    // Necessary to properly update button states.
    installHeadlessTestDataManager(project, projectRule.fixture.testRootDisposable)
    val windowManager = TestToolWindowManager(project)
    toolWindow = windowManager.toolWindow
    project.registerServiceInstance(ToolWindowManager::class.java, windowManager)
  }

  @Test
  fun testTabManagement() {
    val factory = EmulatorToolWindowFactory()
    assertThat(factory.shouldBeAvailable(project)).isTrue()
    factory.createToolWindowContent(project, toolWindow)
    val contentManager = toolWindow.contentManager
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554, standalone = false)
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(tempFolder), 8555, standalone = true)
    val emulator3 = emulatorRule.newEmulator(FakeEmulator.createWatchAvd(tempFolder), 8556, standalone = false)

    // The Emulator tool window is closed.
    assertThat(toolWindow.isVisible).isFalse()

    // Start the first and the second emulators.
    emulator1.start()
    emulator2.start()

    // Send notification that the emulator has been launched.
    val avdInfo = AvdInfo(emulator1.avdId, emulator1.avdFolder.resolve("config.ini").toFile(),
                          emulator1.avdFolder.toString(), mock(), null)
    val commandLine = GeneralCommandLine("/emulator_home/fake_emulator", "-avd", emulator1.avdId, "-qt-hide-window")
    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, project)
    dispatchAllInvocationEvents()

    // The Emulator tool window becomes visible when a headless emulator is launched from Studio.
    assertThat(toolWindow.isVisible).isTrue()
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    emulator1.getNextGrpcCall(2, TimeUnit.SECONDS) { true } // Wait for the initial "getVmState" call.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != "No Running Emulators" }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)

    // Start the third emulator.
    emulator3.start()

    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.size == 2 }

    // The second emulator panel is added but the first one is still selected.
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator3.avdName)
    assertThat(contentManager.contents[1].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[1].isSelected).isTrue()

    for (emulator in listOf(emulator2, emulator3)) {
      val device = mock<IDevice>()
      `when`(device.isEmulator).thenReturn(true)
      `when`(device.serialNumber).thenReturn("emulator-${emulator.serialPort}")
      project.messageBus.syncPublisher(AppDeploymentListener.TOPIC).appDeployedToDevice(device, project)
    }

    // Deploying an app activates the corresponding emulator panel.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].isSelected }

    assertThat(contentManager.contents).hasLength(2)

    // Stop the second emulator.
    emulator3.stop()

    // The panel corresponding the the second emulator goes away.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].isSelected).isTrue()

    // Close the panel corresponding to emulator1.
    contentManager.removeContent(contentManager.contents[0], true)
    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get()) {
      val call = emulator1.getNextGrpcCall(2, TimeUnit.SECONDS)
      assertThat(call.methodName).isEqualTo("android.emulation.control.UiController/closeExtendedControls")
    }
    val call = emulator1.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVmState")
    assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("state: SHUTDOWN")

    // The panel corresponding the the first emulator goes away and is replaced by the placeholder panel.
    assertThat(contentManager.contents.size).isEqualTo(1)
    assertThat(contentManager.contents[0].displayName).isEqualTo("No Running Emulators")
  }

  @Test
  fun testEmulatorCrash() {
    val factory = EmulatorToolWindowFactory()
    assertThat(factory.shouldBeAvailable(project)).isTrue()
    factory.createToolWindowContent(project, toolWindow)
    val contentManager = toolWindow.contentManager
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554, standalone = false)

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    val controllers = RunningEmulatorCatalog.getInstance().updateNow().get()
    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)
    assertThat(controllers).isNotEmpty()
    waitForCondition(2, TimeUnit.SECONDS) { controllers.first().connectionState == EmulatorController.ConnectionState.CONNECTED }

    // Simulate an emulator crash.
    emulator.crash()
    controllers.first().sendKey(KeyboardEvent.newBuilder().setText(" ").build())
    waitForCondition(5, TimeUnit.SECONDS) { contentManager.contents[0].displayName == "No Running Emulators" }
  }

  @Test
  fun testUiStatePreservation() {
    StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.override(true, projectRule.fixture.testRootDisposable)

    val factory = EmulatorToolWindowFactory()
    assertThat(factory.shouldBeAvailable(project)).isTrue()
    factory.createToolWindowContent(project, toolWindow)
    val contentManager = toolWindow.contentManager
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554, standalone = false)

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != "No Running Emulators" }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    assertThat(emulator.extendedControlsVisible).isFalse()
    emulatorController.showExtendedControls(PaneIndex.KEEP_CURRENT)
    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel

    toolWindow.hide()

    // Wait for the extended controls to close.
    waitForCondition(4, TimeUnit.SECONDS) { !emulator.extendedControlsVisible }
    // Wait for the prior visibility state of the extended controls to propagate to Studio.
    waitForCondition(2, TimeUnit.SECONDS) { panel.lastUiState?.extendedControlsShown ?: false }

    toolWindow.show()

    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }
  }

  private val FakeEmulator.avdName
    get() = avdId.replace('_', ' ')

  private class TestToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
    var toolWindow = TestToolWindow(project, this)

    override fun getToolWindow(id: String?): ToolWindow {
      assertThat(id).isEqualTo(EMULATOR_TOOL_WINDOW_ID)
      return toolWindow
    }

    override fun invokeLater(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
  }

  private class TestToolWindow(
    project: Project,
    private val manager: ToolWindowManager
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

    private var available = true
    private var visible = false

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      this.available = available
    }

    override fun setAvailable(available: Boolean) {
      this.available = available
    }

    override fun isAvailable(): Boolean {
      return available
    }

    override fun show(runnable: Runnable?) {
      if (!visible) {
        visible = true
        notifyStateChanged()
      }
    }

    override fun hide(runnable: Runnable?) {
      if (visible) {
        visible = false
        notifyStateChanged()
      }
    }

    override fun isVisible() = visible

    private fun notifyStateChanged() {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager)
    }
  }
}
