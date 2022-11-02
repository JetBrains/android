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

import com.android.testutils.TestUtils.getBinPath
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.asdriver.tests.Adb
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.tests.IdeaTestSuiteBase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.util.regex.Pattern
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
  @Before
  fun setUp() {
    adb.runCommand("logcat", "-c", emulator=emulator)
  }

  @Test
  fun framesReceived() {
    waitFrames(30)
  }

  @Test
  fun keyEvents_letters_lowercase() {
    runEventLogger {
      fakeUi.keyboard.setFocus(deviceView)
      adb.logcat {
        for (char in 'a'..'z') {
          fakeUi.keyboard.type(char.code)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

          waitForLogs(char.androidCode.downUp(), 10.seconds)
        }
      }
    }
  }

  @Test
  fun keyEvents_letters_uppercase() {
    runEventLogger {
      fakeUi.keyboard.setFocus(deviceView)
      adb.logcat {
        for (char in 'A'..'Z') {
          fakeUi.keyboard.type(char.code)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

          waitForLogs(
            listOf(
              ".*: KEY DOWN: $AKEYCODE_SHIFT_LEFT",
              ".*: KEY DOWN: ${char.androidCode}",
              ".*: KEY UP: ${char.androidCode}",
              ".*: KEY UP: $AKEYCODE_SHIFT_LEFT",
            ),
            10.seconds)
        }
      }
    }
  }

  @Test
  fun keyEvents_digits() {
    runEventLogger {
      fakeUi.keyboard.setFocus(deviceView)
      adb.logcat {
        for (char in '0'..'9') {
          fakeUi.keyboard.type(char.code)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

          waitForLogs(char.androidCode.downUp(), 10.seconds)
        }
      }
    }
  }

  @Test
  fun keyEvents_navigationKeyStrokes() {
    val navigationKeyStrokeCases = mapOf(
      KeyEvent.VK_LEFT to AKEYCODE_DPAD_LEFT,
      KeyEvent.VK_KP_LEFT to AKEYCODE_DPAD_LEFT,
      KeyEvent.VK_RIGHT to AKEYCODE_DPAD_RIGHT,
      KeyEvent.VK_KP_RIGHT to AKEYCODE_DPAD_RIGHT,
      KeyEvent.VK_UP to AKEYCODE_DPAD_UP,
      KeyEvent.VK_KP_UP to AKEYCODE_DPAD_UP,
      KeyEvent.VK_DOWN to AKEYCODE_DPAD_DOWN,
      KeyEvent.VK_KP_DOWN to AKEYCODE_DPAD_DOWN,
      KeyEvent.VK_HOME to AKEYCODE_MOVE_HOME,
      KeyEvent.VK_END to AKEYCODE_MOVE_END,
      KeyEvent.VK_PAGE_DOWN to AKEYCODE_PAGE_DOWN,
      KeyEvent.VK_PAGE_UP to AKEYCODE_PAGE_UP,
    )

    runEventLogger {
      fakeUi.keyboard.setFocus(deviceView)
      adb.logcat {
        for ((hostKeyStroke, androidKeyCode) in navigationKeyStrokeCases) {
          fakeUi.keyboard.pressAndRelease(hostKeyStroke)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

          waitForLogs(androidKeyCode.downUp(), 10.seconds)
        }
      }
    }
  }

  @Test
  fun keyEvents_controlCharacters() {
    val controlCharacterCases = mapOf(
      KeyEvent.VK_ENTER to AKEYCODE_ENTER,
      KeyEvent.VK_TAB to AKEYCODE_TAB,
      KeyEvent.VK_ESCAPE to AKEYCODE_ESCAPE,
      KeyEvent.VK_BACK_SPACE to AKEYCODE_DEL,
      KeyEvent.VK_DELETE to if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL,
    )

    runEventLogger {
      fakeUi.keyboard.setFocus(deviceView)
      adb.logcat {
        for ((hostKeyStroke, androidKeyCode) in controlCharacterCases) {
          fakeUi.keyboard.pressAndRelease(hostKeyStroke)
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

          waitForLogs(androidKeyCode.downUp(), 10.seconds)
        }
      }
    }
  }

  @Test
  fun touchEvents_basic() {
    // Wait for at least one frame to be sure that the device's display rectangle is set.
    waitFrames(1)
    assertThat(deviceView.displayRectangle).isNotNull()

    runEventLogger {
      adb.logcat {
        for (x in 50..150 step 10) {
          for (y in 150..250 step 10) {
            fakeUi.mouse.click(x, y)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            waitForLogs(deviceView.toDeviceDisplayCoordinates(Point(x, y))!!.click(), 30.seconds)
          }
        }
      }
    }
  }

  private fun runEventLogger(block: () -> Unit) {
    try {
      adb.runCommand("shell", START_COMMAND, emulator = emulator) {
        val logLine = Pattern.quote("Starting: Intent { flg=0x${NO_ANIMATIONS.toString(16)} cmp=$APP_PKG/.$ACTIVITY }")
        waitForLog(logLine, 30.seconds)
      }
      adb.logcat {
        waitForLog(".*: RESUMED", 10.seconds)
      }
      block()
    }
    finally {
      adb.runCommand("shell", CLEAR_DATA_COMMAND, emulator = emulator) {
        waitForLog("Success", 30.seconds)
      }
    }
  }

  companion object {
    private const val EVENT_LOGGER_TAG = "EventLogger"
    private const val AGENT_TAG = "ScreenSharing"
    private const val APP_PKG = "com.android.tools.eventlogger"
    private const val ACTIVITY = "EventLoggingActivity"
    private const val NO_ANIMATIONS = 65536 // Intent.FLAG_ACTIVITY_NO_ANIMATION
    private const val START_COMMAND = "am start -n $APP_PKG/.$ACTIVITY -f $NO_ANIMATIONS"
    private const val CLEAR_DATA_COMMAND = "pm clear $APP_PKG"

    private val system: AndroidSystem = AndroidSystem.basic()
    private val projectRule = ProjectRule()
    @get:ClassRule
    @get:JvmStatic
    val ruleChain: RuleChain = RuleChain(projectRule, system, EdtRule())

    private lateinit var adb: Adb
    private lateinit var emulator: Emulator
    private lateinit var deviceView: DeviceView
    private lateinit var fakeUi: FakeUi

    private var framesReceived = 0;

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      StudioFlags.DEVICE_MIRRORING_AGENT_LOG_LEVEL.override("debug")

      val adbBinary = resolveWorkspacePath("prebuilts/studio/sdk/linux/platform-tools/adb")
      check(Files.exists(adbBinary))
      check(System.getProperty(AndroidSdkUtils.ADB_PATH_PROPERTY) == null)
      System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, adbBinary.toString())

      adb = system.runAdb()
      emulator = system.runEmulator(Emulator.SystemImage.API_30)
      emulator.waitForBoot()
      adb.waitForDevice(emulator)

      deviceView = DeviceView(
        disposableParent = projectRule.project.earlyDisposable,
        deviceSerialNumber = emulator.serialNumber,
        deviceAbi = "x86_64",
        deviceName = "My Great Device",
        initialDisplayOrientation = 0,
        project = projectRule.project,
      )

      fakeUi = FakeUi(deviceView.wrapInScrollPane(200, 300))
      fakeUi.render()

      waitForCondition(30.seconds) { deviceView.isConnected }

      deviceView.addFrameListener { _, _, _, _ -> framesReceived++ }

      // Install the event logger app
      val eventLoggerApk = getBinPath("tools/adt/idea/emulator/integration/event-logger/event-logger.apk")
      adb.runCommand("install", eventLoggerApk.toString(), emulator = emulator) {
        waitForLog("Success", 30.seconds)
      }
    }

    @JvmStatic
    @AfterClass
    fun tearDownClass() {
      emulator.close()
      waitForCondition(30.seconds) { !deviceView.isConnected }
      adb.close()
    }

    private fun Component.wrapInScrollPane(width: Int, height: Int): JScrollPane {
      return JBScrollPane(this).apply {
        border = null
        isFocusable = true
        size = Dimension(width, height)
      }
    }

    private val Char.androidCode: Int
      get() = when (this) {
        in 'a'..'z' -> this.code - 'a'.code + AKEYCODE_A
        in 'A'..'Z' -> this.code - 'A'.code + AKEYCODE_A
        in '0'..'9' -> this.code - '0'.code + AKEYCODE_0
        else -> throw IllegalArgumentException("Only alphanumeric characters are supported!")
      }

    private fun Adb.logcat(block: Adb.() -> Unit) {
      runCommand("logcat", "$EVENT_LOGGER_TAG:D", "$AGENT_TAG:D", "*:S", emulator = emulator) { block() }
    }

    private fun Int.downUp(): List<String> = listOf(".*: KEY DOWN: $this", ".*: KEY UP: $this")

    private fun Point.click(): List<String> {
      val coordinates = Pattern.quote("(${x.toDouble()},${y.toDouble()})")
      return listOf(".*: TOUCH EVENT: ACTION_DOWN $coordinates", ".*: TOUCH EVENT: ACTION_UP $coordinates")
    }

    private fun waitFrames(numFrames: Int) {
      val framesToWaitFor = framesReceived + numFrames
      waitForCondition(30.seconds) {
        fakeUi.render()
        framesReceived > framesToWaitFor
      }
    }
  }
}
