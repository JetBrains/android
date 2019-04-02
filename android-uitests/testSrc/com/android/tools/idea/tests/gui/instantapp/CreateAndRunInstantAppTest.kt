/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp

import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.fixture.JCheckBoxFixture
import org.fest.swing.util.PatternTextMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.swing.JCheckBox

@RunWith(GuiTestRemoteRunner::class)
class CreateAndRunInstantAppTest {
  @Rule @JvmField val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  private val projectApplicationId = "com.android.devtools.simple"
  private lateinit var fakeAdbServer: FakeAdbServer

  @Before
  fun setupFakeAdbServer() {
    /**
     * ActivityManager "start" command handler for an invocation like
     *
     *    am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'https://example.com/example'
     */
    val startCmdHandler = object: ActivityManagerCommandHandler.ProcessStarter {
      override fun startProcess(deviceState: DeviceState): String {
        deviceState.startClient(1234, 1235, projectApplicationId, false)
        return "Starting: Intent { act=android.intent.action.VIEW cat=[android.intent.category.BROWSABLE] dat=https://example.com/... }"
      }
    }

    fakeAdbServer = FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .addDeviceHandler(ActivityManagerCommandHandler(startCmdHandler))
      .addDeviceHandler(JdwpCommandHandler())
      .build()

    val fakeDevice = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "8.0",
      "26",
      DeviceState.HostConnectionType.LOCAL
    ).get()
    fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE

    fakeAdbServer.start()
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.port)
  }

  /**
   * Verify created instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 84f8150d-0319-4e7e-b510-8227890aca3f
   *
   * <pre>
   *   Test steps:
   *   1. Create an instant app project.
   *   2. Set up an emulator running API 26.
   *   3. Run the instantapp run configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  fun createAndRun() {
    val runConfigName = "app"
    guiTest
      .welcomeFrame()
      .createNewProject()
      .clickNext()
      .configureNewAndroidProjectStep
      .enterPackageName(projectApplicationId)
      .selectMinimumSdkApi("23")
      .setIncludeInstantApp(true)
      .wizard()
      .clickFinish()

    val ideFrame = guiTest.ideFrame()
    // TODO remove the following workaround wait for http://b/72666461
    ideFrame.waitForGradleProjectSyncToFinish()

    // The project is not deployed as an instant app by default anymore. Enable
    // deploying the project as an instant app:
    ideFrame.invokeMenuPath("Run", "Edit Configurations...")
    val configDialog = EditConfigurationsDialogFixture.find(ideFrame.robot())
    val instantAppCheckbox = GuiTests.waitUntilShowing(
      ideFrame.robot(),
      configDialog.target(),
      Matchers.byText(JCheckBox::class.java, "Deploy as instant app")
    )
    val instantAppCheckBoxFixture = JCheckBoxFixture(
      ideFrame.robot(),
      instantAppCheckbox
    )
    instantAppCheckBoxFixture.select()
    configDialog.clickOk()
    configDialog.waitUntilNotShowing()

    ideFrame.runApp(runConfigName, "Google Nexus 5X")

    val runWindow = ideFrame.runToolWindow
    runWindow.activate()
    val runWindowContent = runWindow.findContent(runConfigName)

    val runOutputPattern = Pattern.compile(".*Connected to process.*", Pattern.DOTALL)
    runWindowContent.waitForOutput(PatternTextMatcher(runOutputPattern), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS)

    runWindowContent.waitForStopClick()
  }

  @After
  fun shutdownFakeAdb() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    fakeAdbServer.close()
  }

}
