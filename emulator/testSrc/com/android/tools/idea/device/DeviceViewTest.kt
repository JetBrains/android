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

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Component
import java.awt.Dimension
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JScrollPane

/**
 * Tests for [DeviceView] and [DeviceClient].
 */
@RunsInEdt
internal class DeviceViewTest {
  private val agentRule = FakeScreenSharingAgentRule()
  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(agentRule).around(EdtRule())
  private lateinit var device: FakeScreenSharingAgentRule.FakeDevice

  private val testRootDisposable
    get() = agentRule.testRootDisposable

  @Before
  fun setUp() {
    device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
  }

  @Test
  fun testDeviceView() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    val agent = device.agent
    val view = DeviceView(testRootDisposable, device.serialNumber, device.abi, null, agentRule.project)
    val container = wrapInScrollPane(view, 150, 250)
    val ui = FakeUi(container, 2.0)
    waitForCondition(5, TimeUnit.SECONDS) { agent.started }
    assertThat(agent.commandLine).isEqualTo("CLASSPATH=/data/local/tmp/screen-sharing-agent.jar app_process" +
                                            " /data/local/tmp com.android.tools.screensharing.Main --log=debug --codec=vp8")
    assertThat(agent.getNextControlMessage(2, TimeUnit.SECONDS)).isEqualTo(SetMaxVideoResolutionMessage(300, 500))
    // Wait for all video frames to be received.
    waitForCondition(2, TimeUnit.SECONDS) { ui.render(); view.frameNumber == agent.frameNumber }
    assertAppearance(ui, "Image1")
  }

  private fun wrapInScrollPane(view: Component, width: Int, height: Int): JScrollPane {
    @Suppress("UndesirableClassUsage")
    return JScrollPane(view).apply {
      border = null
      isFocusable = true
      size = Dimension(width, height)
    }
  }

  private fun assertAppearance(ui: FakeUi, goldenImageName: String) {
    val image = ui.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path {
    return TestUtils.resolveWorkspacePathUnchecked("${GOLDEN_FILE_PATH}/${name}.png")
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/emulator/testData/DeviceViewTest/golden"
