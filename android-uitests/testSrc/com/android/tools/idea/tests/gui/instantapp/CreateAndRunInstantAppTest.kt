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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.util.StringTextMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class CreateAndRunInstantAppTest {
  @Rule @JvmField val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  @Rule @JvmField val emulator = EmulatorTestRule()

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
        deviceState.startClient(1234, 1235, "${projectApplicationId}.app", false)
        return "Starting: Intent { act=android.intent.action.VIEW cat=[android.intent.category.BROWSABLE] dat=https://example.com/... }"
      }
    }

    fakeAdbServer = FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .setShellCommandHandler(ActivityManagerCommandHandler.COMMAND, {
        ActivityManagerCommandHandler(
          startCmdHandler
        )
      })
      .setDeviceCommandHandler(JdwpCommandHandler.COMMAND, {
        JdwpCommandHandler()
      })
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
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/79937083
  fun createAndRun() {
    val runConfigName = "instantapp"
    val newProj = guiTest.welcomeFrame().createNewProject()

    if (StudioFlags.NPW_DYNAMIC_APPS.get()) {
      newProj
        .clickNext()
        .configureNewAndroidProjectStep
        .enterPackageName(projectApplicationId)
        .selectMinimumSdkApi("23")
        .setIncludeInstantApp(true)
        .wizard()
        .clickFinish()
    }
    else {
      newProj.configureAndroidProjectStep
        .enterPackageName(projectApplicationId)
      newProj.clickNext()
      newProj.configureFormFactorStep
        .selectMinimumSdkApi(FormFactor.MOBILE, "23")
        .findInstantAppCheckbox(FormFactor.MOBILE)
        .select()

      newProj.clickNext()
        .clickNext()
        .clickNext()
        .clickFinish()
    }

    val ideFrame = guiTest.ideFrame()
    // TODO remove the following workaround wait for http://b/72666461
    guiTest.testSystem().waitForProjectSyncToFinish(ideFrame)

    ideFrame.runApp(runConfigName)
      .selectDevice(StringTextMatcher("Google Nexus 5X"))
      .clickOk()

    val runWindow = ideFrame.runToolWindow
    runWindow.activate()
    val runWindowContent = runWindow.findContent(runConfigName)
    emulator.waitForProcessToStart(runWindowContent)

    runWindowContent.waitForStopClick()
  }

  @After
  fun shutdownFakeAdb() {
    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disableFakeAdbServerMode()
    fakeAdbServer.close()
  }

}
