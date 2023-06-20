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
package com.android.tools.idea.tests.gui.emulator.end2end

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EmulatorToolWindowFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.fest.swing.util.PatternTextMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

private const val APP_NAME = "app"
private val RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL)

/**
 * End-to-end test of the Embedded Emulator. Uses real emulator. Runs in IntelliJ and in Bazel.
 */
@RunWith(GuiTestRemoteRunner::class)
class EmbeddedEmulatorTest {
  private val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)
  private val emulatorRule = EmulatorRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(guiTest).around(emulatorRule)

  /**
   * Measures screen update latency when typing in the Emulator tool window.
   */
  @Test
  fun testTypingLatency() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("TextInput")
    assertThat(ideFrame.invokeProjectMake().isBuildSuccessful).isTrue()

    emulatorRule.waitUntilBooted(120, TimeUnit.SECONDS)

    // Run the app.
    ideFrame.runApp(APP_NAME, emulatorRule.avdId, Wait.seconds(120))
    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the emulator.
    ideFrame.runToolWindow.findContent(APP_NAME).waitForOutput(PatternTextMatcher(RUN_OUTPUT), 10)

    val emulatorWindow = EmulatorToolWindowFixture(ideFrame)
    val emulatorView = emulatorWindow.waitForEmulatorViewToShow(Wait.seconds(10))
    val robot = ideFrame.robot()
    robot.focus(emulatorView)
    // Wait the "Viewing full screen" prompt to appear.
    emulatorView.waitForPeriodWithoutNewFrames(Duration.ofSeconds(2))
    var frameNumber = emulatorView.frameNumber
    // Type Space twice to dismiss the "Viewing full screen" prompt.
    robot.type(' ')
    robot.type(' ')

    // Let the dust settle.
    emulatorView.waitForFrame(frameNumber + 1, 2, TimeUnit.SECONDS)
    emulatorView.waitForPeriodWithoutNewFrames(Duration.ofSeconds(3))
    frameNumber = emulatorView.frameNumber
    val start = System.currentTimeMillis()
    val text = "The quick brown fox jumps over the lazy dog"
    for (c in text) {
      robot.type(c)
    }
    val typingTimePerCharacter = (System.currentTimeMillis() - start).toDouble() / text.length
    // Wait for a calm period after at least one new frame.
    emulatorView.waitForFrame(frameNumber + 1, 2, TimeUnit.SECONDS)
    emulatorView.waitForPeriodWithoutNewFrames(Duration.ofSeconds(2))

    val timePerCharacter = (emulatorView.frameTimestampMillis - start).toDouble() / text.length
    val numFrames = emulatorView.frameNumber - frameNumber
    println(String.format("****** Average screen update latency is %.3g ms per character, typing speed %.3g ms per character, " +
                          "%d frame updates for %d characters",
                          timePerCharacter, typingTimePerCharacter, numFrames, text.length))
    assertThat(timePerCharacter).isLessThan(200.0)
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { frameNumber >= frame }
  }

  /**
   * Waits for the time interval without new frames.
   */
  private fun EmulatorView.waitForPeriodWithoutNewFrames(duration: Duration) {
    while (true) {
      val calmPeriod = System.currentTimeMillis() - frameTimestampMillis
      if (calmPeriod >= duration.toMillis()) {
        return
      }
      Thread.sleep(duration.toMillis() - calmPeriod)
    }
  }
}