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
package com.android.tools.idea.device

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.asdriver.tests.Adb
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import java.awt.Component
import java.awt.Dimension
import java.nio.file.Files
import javax.swing.JScrollPane
import kotlin.time.Duration.Companion.seconds

@RunWith(Suite::class)
@SuiteClasses(ScreenSharingAgentTest::class)
class ScreenSharingAgentTestSuite : IdeaTestSuiteBase()

/**
 * Tests the functionality of the Screen Sharing Agent.
 *
 * This works by starting an emulator and then creating a [DeviceView] that starts screen sharing on the emulator.
 */
@RunWith(JUnit4::class)
@RunsInEdt
class ScreenSharingAgentTest {
  @Test
  fun framesReceived() {
    val framesToWaitFor = 30
    var framesReceived = 0
    deviceView.addFrameListener { _, _, _, _ -> framesReceived++ }

    waitForCondition(30.seconds) {
      fakeUi.render()
      framesReceived > framesToWaitFor
    }
  }

  companion object {
    private val system: AndroidSystem = AndroidSystem.basic()
    private val projectRule = ProjectRule()
    @get:ClassRule
    @get:JvmStatic
    val ruleChain: RuleChain = RuleChain(projectRule, system, EdtRule())

    private lateinit var adb: Adb
    private lateinit var emulator: Emulator
    private lateinit var deviceView: DeviceView
    private lateinit var fakeUi: FakeUi

    @JvmStatic
    @BeforeClass
    fun setUp() {
      val adbBinary = resolveWorkspacePath("prebuilts/studio/sdk/linux/platform-tools/adb")
      check(Files.exists(adbBinary))
      check(System.getProperty(AndroidSdkUtils.ADB_PATH_PROPERTY) == null)
      System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, adbBinary.toString())

      adb = system.runAdb()
      emulator = system.runEmulator()
      emulator.waitForBoot()
      adb.waitForDevice(emulator)

      deviceView = DeviceView(
        disposableParent = projectRule.project.earlyDisposable,
        deviceSerialNumber = "emulator-${emulator.portString}",
        deviceAbi = "x86_64",
        deviceName = "My Great Device",
        initialDisplayOrientation = 0,
        project = projectRule.project,
      )

      fakeUi = FakeUi(wrapInScrollPane(deviceView, 200, 300))
      fakeUi.render()

      waitForCondition(30.seconds) { deviceView.isConnected }
    }

    @JvmStatic
    @AfterClass
    fun tearDown() {
      emulator.close()
      waitForCondition(30.seconds) { !deviceView.isConnected }
      adb.close()
    }

    private fun wrapInScrollPane(view: Component, width: Int, height: Int): JScrollPane {
      return JBScrollPane(view).apply {
        border = null
        isFocusable = true
        size = Dimension(width, height)
      }
    }
  }
}