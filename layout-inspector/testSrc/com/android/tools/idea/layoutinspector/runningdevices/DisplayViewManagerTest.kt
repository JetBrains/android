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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Rectangle
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunsInEdt
class DisplayViewManagerTest {

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val displayViewRule = EmulatorViewRule()

  private lateinit var renderModel: RenderModel
  private lateinit var displayViewManager: DisplayViewManager
  private lateinit var displayView: EmulatorView

  @Before
  fun setUp() {
    val inspectorModel = model {
      view(ROOT, Rectangle(0, 0, 100, 200)) {
        view(VIEW1, Rectangle(10, 10, 50, 100))
      }
    }
    val treeSettings = FakeTreeSettings()

    renderModel = RenderModel(inspectorModel, treeSettings)
    val renderLogic = RenderLogic(renderModel, FakeRenderSettings())
    displayView = displayViewRule.newEmulatorView()
    displayViewManager = DisplayViewManager(renderModel, renderLogic, displayView)
  }

  @Test
  @Ignore("b/265150325 test fails only on presubmit. Re enable once we have a better way of dealing with EmulatorViewRule")
  fun testDisplayViewRepaintOnModelModifications() {
    var viewPainted = false
    displayView.addDecorationRenderer { _, _, _, _ -> viewPainted = true }

    val fakeUi = FakeUi(displayView)

    displayView.size = Dimension(200, 300)
    fakeUi.layoutAndDispatchEvents()

    displayViewManager.startRendering()

    // invoke the modification listener added by DisplayViewManager
    renderModel.modificationListeners.first().invoke()

    getStreamScreenshotCallAndWaitForFrame(fakeUi, displayView, 1)
    fakeUi.render()

    assertThat(viewPainted).isTrue()

    displayViewManager.stopRendering()

    assertThat(renderModel.modificationListeners).isEmpty()
  }

  private fun getStreamScreenshotCallAndWaitForFrame(fakeUi: FakeUi, view: EmulatorView, frameNumber: Int): FakeEmulator.GrpcCallRecord {
    val emulator = displayViewRule.getFakeEmulator(view)
    val call = emulator.getNextGrpcCall(2, TimeUnit.SECONDS)
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/streamScreenshot")
    view.waitForFrame(fakeUi, frameNumber, 2, TimeUnit.SECONDS)
    return call
  }

  @Throws(TimeoutException::class)
  private fun EmulatorView.waitForFrame(fakeUi: FakeUi, frame: Int, timeout: Long, unit: TimeUnit) {
    waitForCondition(timeout, unit) { renderAndGetFrameNumber(fakeUi) >= frame }
  }

  private fun EmulatorView.renderAndGetFrameNumber(fakeUi: FakeUi): Int {
    fakeUi.render() // The frame number may get updated as a result of rendering.
    return frameNumber
  }
}