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
package com.android.tools.idea.layoutinspector.tree

import com.android.testutils.waitForCondition
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcess
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.EdtRule
import com.intellij.ui.components.JBLoadingPanel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

class RootPanelTest {

  private val androidProjectRule = AndroidProjectRule.inMemory()

  private val appInspectorRule = AppInspectionInspectorRule(androidProjectRule)
  private val layoutInspectorRule = LayoutInspectorRule(listOf(appInspectorRule.createInspectorClientProvider()), androidProjectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(androidProjectRule)
    .around(appInspectorRule)
    .around(layoutInspectorRule)
    .around(EdtRule())!!

  private val fakeDeviceDescriptor = object : DeviceDescriptor {
    override val manufacturer = "manufacturer"
    override val model = "model"
    override val serial = "serial"
    override val isEmulator = false
    override val apiLevel = 0
    override val version = "version"
    override val codename = "codename"
  }

  @Test
  fun testProcessNotDebuggablePanelIsShown() {
    LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = true

    val fakeTreePanel = JPanel()
    val rootPanel = RootPanel(androidProjectRule.testRootDisposable, fakeTreePanel)

    assertThat(rootPanel.showProcessNotDebuggableText).isFalse()

    rootPanel.layoutInspector = layoutInspectorRule.inspector

    layoutInspectorRule.fakeForegroundProcessDetection.addNewForegroundProcess(
      fakeDeviceDescriptor, ForegroundProcess(0, "fakeprocess"), false
    )
    assertThat(rootPanel.showProcessNotDebuggableText).isTrue()

    layoutInspectorRule.fakeForegroundProcessDetection.addNewForegroundProcess(
      fakeDeviceDescriptor, ForegroundProcess(0, "fakeprocess"), true
    )
    assertThat(rootPanel.showProcessNotDebuggableText).isFalse()

    // disable embedded Layout Inspector, showProcessNotDebuggableText should always be false
    LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = false
    rootPanel.layoutInspector = layoutInspectorRule.inspector

    layoutInspectorRule.fakeForegroundProcessDetection.addNewForegroundProcess(
      fakeDeviceDescriptor, ForegroundProcess(0, "fakeprocess"), false
    )
    assertThat(rootPanel.showProcessNotDebuggableText).isFalse()
  }

  @Test
  fun testLoadingPanelIsShown() {
    val fakeTreePanel = JPanel()
    val rootPanel = RootPanel(androidProjectRule.testRootDisposable, fakeTreePanel)
    rootPanel.layoutInspector = layoutInspectorRule.inspector

    assertThat(rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading).isFalse()

    // Start connecting, loading should show
    layoutInspectorRule.launchSynchronously = false
    layoutInspectorRule.startLaunch(2)
    layoutInspectorRule.processes.selectedProcess = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

    waitForCondition(1, TimeUnit.SECONDS) { rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading }

    // Release the response from the agent and wait for connection.
    // The loading should stop and the empty text should not be visible, because now we are connected and showing views on screen
    layoutInspectorRule.awaitLaunch()

    waitForCondition(1, TimeUnit.SECONDS) { !rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading }
  }

  @Test
  fun testLoadingPanelIsRemovesProcessNotDebuggable() {
    LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled = true

    val fakeTreePanel = JPanel()
    val rootPanel = RootPanel(androidProjectRule.testRootDisposable, fakeTreePanel)
    rootPanel.layoutInspector = layoutInspectorRule.inspector

    layoutInspectorRule.fakeForegroundProcessDetection.addNewForegroundProcess(
      fakeDeviceDescriptor, ForegroundProcess(0, "fakeprocess"), false
    )
    assertThat(rootPanel.showProcessNotDebuggableText).isTrue()

    assertThat(rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading).isFalse()

    // Start connecting, loading should show
    layoutInspectorRule.launchSynchronously = false
    layoutInspectorRule.startLaunch(2)
    layoutInspectorRule.processes.selectedProcess = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

    waitForCondition(1, TimeUnit.SECONDS) { rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading }

    assertThat(rootPanel.showProcessNotDebuggableText).isFalse()

    // Release the response from the agent and wait for connection.
    // The loading should stop and the empty text should not be visible, because now we are connected and showing views on screen
    layoutInspectorRule.awaitLaunch()

    waitForCondition(1, TimeUnit.SECONDS) { !rootPanel.components.filterIsInstance<JBLoadingPanel>().first().isLoading }
  }
}