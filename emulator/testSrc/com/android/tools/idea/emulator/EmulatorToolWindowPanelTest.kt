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

import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.FakeUi.setPortableUiFont
import com.android.tools.idea.emulator.FakeEmulator.GrpcCallRecord
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.UiTestRule
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Tests for [EmulatorToolWindowPanel].
 */
@RunsInEdt
class EmulatorToolWindowPanelTest {
  companion object {
    @JvmField
    @ClassRule
    val uiRule = UiTestRule.uiRule
  }

  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private var nullableEmulator: FakeEmulator? = null
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(emulatorRule).around(EdtRule())

  var emulator: FakeEmulator
    get() = nullableEmulator ?: throw IllegalStateException()
    set(value) { nullableEmulator = value }

  @Before
  fun setUp() {
    setPortableUiFont()
    // Necessary to properly update button states.
    installHeadlessTestDataManager(projectRule.project, projectRule.fixture.testRootDisposable)
  }

  @Test
  fun testEmulatorToolWindowPanel() {
    val panel = createWindowPanel()
    val ui = FakeUi(panel)

    assertThat(panel.emulatorView).isNull()

    panel.createContent(false)
    assertThat(panel.emulatorView).isNotNull()

    // Check appearance.
    panel.zoomToolbarIsVisible = true
    var frameNumber = panel.emulatorView!!.frameNumber
    assertThat(frameNumber).isEqualTo(0)
    panel.size = Dimension(400, 600)
    ui.layoutAndDispatchEvents()
    val call = getStreamScreenshotCallAndWaitForFrame(panel, ++frameNumber)
    assertThat(shortDebugString(call.request)).isEqualTo("format: RGBA8888 width: 253 height: 521")
    assertAppearance(ui, "image1")

    panel.showLongRunningOperationIndicator("Just a sec...")
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "image2")

    panel.hideLongRunningOperationIndicator()
    ui.layoutAndDispatchEvents()
    assertAppearance(ui, "image1")

    panel.destroyContent()
    assertThat(panel.emulatorView).isNull()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(panel: EmulatorToolWindowPanel, frameNumber: Int): GrpcCallRecord {
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    panel.waitForFrame(frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  private fun createWindowPanel(): EmulatorToolWindowPanel {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), 8554)
    emulator.start()
    val emulators = catalog.updateNow().get()
    assertThat(emulators).hasSize(1)
    val emulatorController = emulators.first()
    val panel = EmulatorToolWindowPanel(emulatorController)
    waitForCondition(2, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    emulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getStatus" call.
    return panel
  }

  @Throws(TimeoutException::class)
  private fun waitForCondition(timeout: Long, unit: TimeUnit, condition: () -> Boolean) {
    val timeoutMillis = unit.toMillis(timeout)
    val deadline = System.currentTimeMillis() + timeoutMillis
    var waitUnit = ((timeoutMillis + 9) / 10).coerceAtMost(10)
    while (waitUnit > 0) {
      dispatchAllInvocationEvents()
      if (condition()) {
        return
      }
      Thread.sleep(waitUnit)
      waitUnit = waitUnit.coerceAtMost(deadline - System.currentTimeMillis())
    }
    throw TimeoutException()
  }

  @Throws(TimeoutException::class)
  private fun EmulatorToolWindowPanel.waitForFrame(frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { emulatorView!!.frameNumber >= frame }
  }

  private val EmulatorToolWindowPanel.emulatorView
    get() = getData(EMULATOR_VIEW_KEY.name) as EmulatorView?

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    ui.updateToolbars()
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.03)
  }

  private fun getGoldenFile(name: String): File {
    return TestUtils.getWorkspaceRoot().toPath().resolve("${GOLDEN_FILE_PATH}/${name}.png").toFile()
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/EmulatorToolWindowPanelTest/golden"
