/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.swing.JCheckBox

@RunsInEdt
class WearHealthServicesToolWindowTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  private val testDataPath: Path
    get() = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/wear-whs/testData")

  private val deviceManager by lazy { FakeDeviceManager() }
  private val stateManager by lazy { WearHealthServicesToolWindowStateManagerImpl(deviceManager) }
  private val toolWindow by lazy {
    WearHealthServicesToolWindow(stateManager).apply {
      setSerialNumber("test")
    }
  }

  @Before
  fun setUp() {
    Disposer.register(projectRule.testRootDisposable, stateManager)
    Disposer.register(projectRule.testRootDisposable, toolWindow)
  }

  @Test
  fun `test panel screenshot matches expectation for current platform`() = runBlocking {
    val fakeUi = FakeUi(toolWindow)

    fakeUi.waitForCheckbox("Heart rate", true)
    fakeUi.waitForCheckbox("Location", true)
    fakeUi.waitForCheckbox("Steps", true)

    fakeUi.root.size = Dimension(400, 400)
    fakeUi.layoutAndDispatchEvents()

    ImageDiffUtil.assertImageSimilarPerPlatform(
      testDataPath = testDataPath,
      fileNameBase = "screens/whs-panel-default",
      actual = fakeUi.render(),
      maxPercentDifferent = 3.0)
  }

  @Test
  fun `test panel screenshot matches expectation with modified state manager values`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(deviceManager.capabilities)

    deviceManager.failState = true

    stateManager.getCapabilitiesList().waitForValue(WHS_CAPABILITIES)

    stateManager.setPreset(Preset.CUSTOM)
    stateManager.setCapabilityEnabled(deviceManager.capabilities[0], true)
    stateManager.setCapabilityEnabled(deviceManager.capabilities[1], false)
    stateManager.setCapabilityEnabled(deviceManager.capabilities[2], false)
    stateManager.setOverrideValue(deviceManager.capabilities[0], 2f)
    stateManager.setOverrideValue(deviceManager.capabilities[2], 5f)
    stateManager.applyChanges()

    val fakeUi = FakeUi(toolWindow)

    fakeUi.waitForCheckbox("Heart rate", true)
    fakeUi.waitForCheckbox("Location", false)
    fakeUi.waitForCheckbox("Steps", false)

    fakeUi.root.size = Dimension(400, 400)
    fakeUi.layoutAndDispatchEvents()

    ImageDiffUtil.assertImageSimilarPerPlatform(
      testDataPath = testDataPath,
      fileNameBase = "screens/whs-panel-state-manager-modified",
      actual = fakeUi.render(),
      maxPercentDifferent = 3.0)
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitForCheckbox(text: String, selected: Boolean) = waitForCondition(10, TimeUnit.SECONDS) {
    root.findDescendant<JCheckBox> { it.text.contains(text) && it.isSelected == selected } != null
  }

  private suspend fun <T> StateFlow<T>.waitForValue(value: T, timeout: Long = 1000) {
    val received = mutableListOf<T>()
    try {
      withTimeout(timeout) { takeWhile { it != value }.collect { received.add(it) } }
    }
    catch (ex: TimeoutCancellationException) {
      Assert.fail("Timed out waiting for value $value. Received values so far $received")
    }
  }
}
