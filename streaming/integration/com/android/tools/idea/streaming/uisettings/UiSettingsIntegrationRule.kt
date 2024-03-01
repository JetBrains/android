/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.uisettings

import com.android.adblib.DeviceSelector
import com.android.flags.junit.FlagRule
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.asdriver.tests.Adb
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.android.tools.idea.streaming.device.DeviceClient
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.DeviceToolWindowPanel
import com.android.tools.idea.streaming.device.DeviceView
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.uisettings.ui.UiSettingsPanel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.utils.executeWithRetries
import com.google.common.truth.Truth
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.rd.util.forEachReversed
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

private const val SETTINGS_BUTTON_TEXT = "Device UI Shortcuts"

internal enum class TestDeviceType {
  EMULATOR,
  DEVICE
}

/**
 * Rule for setting up integration tests for UI Settings Shortcuts on an emulator or a device.
 */
internal class UiSettingsIntegrationRule : ExternalResource() {
  private val popupRule = JBPopupRule()
  private val disposableRule = DisposableRule()
  private val gestureRule = FlagRule(StudioFlags.EMBEDDED_EMULATOR_GESTURE_NAVIGATION_IN_UI_SETTINGS, true)
  private val debugLayoutRule = FlagRule(StudioFlags.EMBEDDED_EMULATOR_DEBUG_LAYOUT_IN_UI_SETTINGS, true)
  private val timeoutRule = FlagRule(StudioFlags.DEVICE_MIRRORING_CONNECTION_TIMEOUT_MILLIS, 30_000)
  private val projectRule = AndroidProjectRule.withAndroidModel(
    createAndroidProjectBuilderForDefaultTestProjectStructure()
      .copy(applicationIdFor = { APPLICATION_ID })
  )

  private val root = Files.createTempDirectory("root")
  private val system: AndroidSystem = AndroidSystem.basic(root)
  private var testType = TestDeviceType.EMULATOR

  internal val project
    get() = projectRule.project
  internal val serialNumber: String
    get() = emulator.serialNumber
  private val testRootDisposable
    get() = disposableRule.disposable

  private lateinit var adb: Adb
  private lateinit var fakeUi: FakeUi
  private lateinit var toolWindow: StreamingDevicePanel
  private lateinit var emulator: Emulator

  fun onDevice(): UiSettingsIntegrationRule {
    testType = TestDeviceType.DEVICE
    return this
  }

  override fun apply(base: Statement, description: Description): Statement =
    apply(base, description, projectRule, popupRule, disposableRule, gestureRule, debugLayoutRule, timeoutRule)

  private fun apply(base: Statement, description: Description, vararg rules: TestRule): Statement {
    var statement = super.apply(base, description)
    rules.forEachReversed { statement = it.apply(statement, description) }
    return statement
  }

  override fun before() {
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable) // Necessary to properly update toolbar button states.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(project), testRootDisposable)

    adb = initAdb()
    emulator = system.runEmulator(Emulator.SystemImage.API_35, listOf("-qt-hide-window"))
    emulator.waitForBoot()
    installTestApplication()
    toolWindow = createStreamingDevicePanel()
    startRunningDeviceWindowUi()
  }

  override fun after() {
    runInEdtAndWait { toolWindow.destroyContent() }
    Disposer.dispose(toolWindow)
    emulator.close()
    System.clearProperty(AndroidSdkUtils.ADB_PATH_PROPERTY)
    val catalog = RunningEmulatorCatalog.getInstance()
    catalog.updateNow()
    ignoreAllThreadLeaks()
  }

  internal fun openUiSettings(): UiSettingsPanel {
    waitForCondition(60.seconds) {
      toolWindow.updateMainToolbar()
      val button = fakeUi.findComponent<ActionButton> {
        it.action.templateText == SETTINGS_BUTTON_TEXT
      }
      Logger.getInstance(UiSettingsIntegrationRule::class.java).warn("Button found: $button, enabled: ${button?.isEnabled}, showing: ${button?.isShowing}")
      button?.isEnabled ?: false
    }
    val button = fakeUi.getComponent<ActionButton> { it.action.templateText == SETTINGS_BUTTON_TEXT }
    button.click()
    waitForCondition(30.seconds) { popupRule.fakePopupFactory.balloonCount > 0 }
    val balloon = popupRule.fakePopupFactory.getNextBalloon()
    Truth.assertThat(balloon).isNotNull()
    return balloon.component as UiSettingsPanel
  }

  private fun initAdb(): Adb {
    val adbBinary = TestUtils.resolveWorkspacePath("prebuilts/studio/sdk/linux/platform-tools/adb")
    check(Files.exists(adbBinary))
    check(System.getProperty(AndroidSdkUtils.ADB_PATH_PROPERTY) == null)
    System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, adbBinary.toString())
    return system.runAdb()
  }

  private fun installTestApplication() {
    val languagesApk = TestUtils.getBinPath("tools/adt/idea/streaming/integration/languages/languages.apk")
    executeWithRetries<InterruptedException>(3) {
      adb.runCommand("install", languagesApk.toString(), emulator = emulator) {
        waitForLog("Success", 20.seconds)
      }
    }
    addStringResources()
  }

  private fun addStringResources() {
    val fixture = projectRule.fixture
    val testAppPath = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/streaming/integration/languages/app/src/main")
    fixture.testDataPath = testAppPath.pathString
    runInEdtAndWait { fixture.copyDirectoryToProject("res", "res") }
  }

  private fun createStreamingDevicePanel() : StreamingDevicePanel {
    return when (testType) {
      TestDeviceType.EMULATOR -> createEmulatorToolWindowPanel()
      TestDeviceType.DEVICE -> runBlocking { createDeviceToolWindowPanel() }
    }
  }

  private fun createEmulatorToolWindowPanel(): EmulatorToolWindowPanel {
    val emulatorController = getControllerOf(root, emulator)
    val panel = EmulatorToolWindowPanel(testRootDisposable, project, emulatorController)
    waitForCondition(10.seconds) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    return panel
  }

  private fun getControllerOf(testRoot: Path, emulator: Emulator): EmulatorController {
    val catalog = RunningEmulatorCatalog.getInstance()
    catalog.overrideRegistrationDirectory(testRoot.resolve("home/.android/avd/running"))
    val emulators = catalog.updateNow().get()
    val emulatorController = emulators.single { emulator.serialNumber == it.emulatorId.serialNumber }
    return emulatorController
  }

  private suspend fun createDeviceToolWindowPanel(): DeviceToolWindowPanel {
    val emptyDeviceConfiguration = DeviceConfiguration(
      DeviceProperties.buildForTest {
        icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        resolution = Resolution(1080, 1920)
        density = 480
      }
    )
    val deviceClient = DeviceClient(emulator.serialNumber, emptyDeviceConfiguration, "x86_64")
    Disposer.register(testRootDisposable, deviceClient)
    val deviceProvisioner = project.getService(DeviceProvisionerService::class.java).deviceProvisioner
    val selector = DeviceSelector.fromSerialNumber(emulator.serialNumber)
    val handle = deviceProvisioner.findConnectedDeviceHandle(selector, Duration.ofSeconds(30)) ?: error("No handle found")
    return DeviceToolWindowPanel(testRootDisposable, project, handle, deviceClient)
  }

  private fun startRunningDeviceWindowUi() {
    runInEdtAndWait {
      val scrollPane = JBScrollPane(toolWindow).apply {
        border = null
        isFocusable = true
        size = Dimension(200, 300)
      }
      fakeUi = FakeUi(scrollPane, createFakeWindow = true, parentDisposable = testRootDisposable)
      toolWindow.createContent(true)
      fakeUi.layoutAndDispatchEvents()
      fakeUi.render()
      if (testType == TestDeviceType.DEVICE) {
        waitForDeviceInitialization(toolWindow, fakeUi)
      }
    }
  }

  private fun waitForDeviceInitialization(streamingDevicePanel: StreamingDevicePanel, fakeUi: FakeUi) {
    val panel = streamingDevicePanel as DeviceToolWindowPanel
    val deviceView = panel.primaryDisplayView!!
    waitForCondition(30.seconds) { renderAndGetFrameNumber(fakeUi, deviceView) > 0u }
  }

  private fun renderAndGetFrameNumber(fakeUi: FakeUi, deviceView: DeviceView): UInt {
    fakeUi.render()
    return deviceView.frameNumber
  }

  // This is a standalone test that is run with bazel without starting Studio.
  // There is no need to check for thread leaks in these tests.
  private fun ignoreAllThreadLeaks() {
    ThreadLeakTracker.longRunningThreadCreated(
      ApplicationManager.getApplication(),
      ProcessIOExecutorService.POOLED_THREAD_PREFIX,
      "JavaCPP Deallocator",
      "InnocuousThread-"
    )
  }

  // Emulate a disconnect of the device
  fun cutConnectionToAgent() {
    if (testType != TestDeviceType.DEVICE) {
      error("Only intended to be used with a device test")
    }
    val panel = toolWindow as DeviceToolWindowPanel
    val deviceView = panel.primaryDisplayView!!
    killAdbServer()
    waitForCondition(30.seconds) { !deviceView.isConnected }
  }

  private fun killAdbServer() {
    adb.runCommand("kill-server")
    adb.close()
    adb = system.runAdb()
  }
}
