/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.runningdevices.ui.SelectedTabState
import com.android.tools.idea.layoutinspector.runningdevices.ui.TabComponents
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.android.tools.idea.streaming.core.StreamingDevicePanel
import com.android.tools.idea.streaming.emulator.EmulatorDisplayPanel
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.kotlin.mock

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 1.0

@RunsInEdt
class EmbeddedLayoutInspectorInjectionTest {
  companion object {
    @JvmField @ClassRule val iconRule = IconLoaderRule()
  }

  private val emulatorViewRule = EmulatorViewRule()
  private val testName = TestName()

  @get:Rule val ruleChain = RuleChain(emulatorViewRule, testName, PortableUiFontRule(), EdtRule())

  private val disposable
    get() = emulatorViewRule.disposable

  private val project
    get() = emulatorViewRule.project

  private lateinit var layoutInspector: LayoutInspector

  /** The dimension of the canvas/panel */
  private val screenDimension = Dimension(500, 500)

  @Before
  fun setUp() {

    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(disposable, processModel)
    val notificationModel = NotificationModel(project)

    val coroutineScope = disposable.createCoroutineScope()
    val launcher =
      InspectorClientLauncher(
        processModel,
        emptyList(),
        project,
        notificationModel,
        coroutineScope,
        disposable,
        metrics = mock(),
      )

    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()

    val model = model(disposable) { view(ROOT, 10, 20, 30, 40) }

    layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = processModel,
        deviceModel = deviceModel,
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(project),
        launcher = launcher,
        layoutInspectorModel = model,
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )
  }

  @Test
  fun testInjectedIntoRunningDevices() = runTest {
    val (panel, selectedTabState) = createUi()

    selectedTabState.enableLayoutInspector()
    testScheduler.advanceUntilIdle()

    renderAndAssertImageSimilarity(panel, selectedTabState.tabComponents)
  }

  @Test
  fun testRemovedFromRunningDevices() = runTest {
    val (panel, selectedTabState) = createUi()

    selectedTabState.enableLayoutInspector()
    testScheduler.advanceUntilIdle()

    Disposer.dispose(selectedTabState)
    testScheduler.advanceUntilIdle()

    renderAndAssertImageSimilarity(panel, selectedTabState.tabComponents)
  }

  private fun createUi(): Pair<StreamingDevicePanel<EmulatorDisplayPanel>, SelectedTabState> {
    val panel = emulatorViewRule.newEmulatorToolWindowPanel()

    val context =
      CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
        panel.uiDataSnapshot(sink)
      }
    val streamingContent = STREAMING_CONTENT_PANEL_KEY.getData(context)
    assertThat(streamingContent).isNotNull()

    val tabComponents =
      TabComponents(disposable = panel, tabContentPanel = streamingContent!!, displayOwner = panel)

    val selectedTabState =
      SelectedTabState(
        disposable = panel,
        project = project,
        deviceId = DeviceId.ofPhysicalDevice("0"),
        tabComponents = tabComponents,
        layoutInspector = layoutInspector,
      )

    return panel to selectedTabState
  }

  private fun renderAndAssertImageSimilarity(
    panel: StreamingDevicePanel<EmulatorDisplayPanel>,
    tabComponents: TabComponents,
  ) {
    val rootPanel = BorderLayoutPanel()
    rootPanel.size = Dimension(screenDimension.width, screenDimension.height)
    rootPanel.addToCenter(panel)
    val fakeUi = FakeUi(rootPanel)
    waitForFrame(fakeUi, tabComponents.displayList.value)
    val image = fakeUi.render()

    assertSimilar(image, testName.methodName)
  }

  private fun assertSimilar(
    renderImage: BufferedImage,
    imageName: String,
    maxDiff: Double = DIFF_THRESHOLD,
  ) {
    val testDataPath = TEST_DATA_PATH.resolve(this.javaClass.simpleName)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked(testDataPath.resolve("$imageName.png").pathString),
      renderImage,
      maxDiff,
    )
  }

  /** Wait until the displays have rendered their UI */
  private fun waitForFrame(fakeUi: FakeUi, displays: List<AbstractDisplayView>) {
    displays.forEach { display ->
      waitForCondition(20.seconds) {
        fakeUi.render()
        display.frameNumber >= 1u
      }
    }
  }
}
