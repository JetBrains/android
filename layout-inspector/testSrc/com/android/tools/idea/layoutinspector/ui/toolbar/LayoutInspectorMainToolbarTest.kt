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
package com.android.tools.idea.layoutinspector.ui.toolbar

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.DEVICE_1
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.runningdevices.allChildren
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS = DEVICE_1.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class LayoutInspectorMainToolbarTest {
  private val androidProjectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val appInspectorRule = AppInspectionInspectorRule(androidProjectRule, withDefaultResponse = false)
  private val layoutInspectorRule =
    LayoutInspectorRule(
      clientProviders = listOf(appInspectorRule.createInspectorClientProvider()),
      projectRule = androidProjectRule,
      isPreferredProcess = { it.name == MODERN_PROCESS.name },
    )

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(androidProjectRule).around(appInspectorRule).around(layoutInspectorRule).around(EdtRule())

  @Before
  fun setUp() {
    layoutInspectorRule.attachDevice(DEVICE_1)
  }

  @After
  fun tearDown() {
    runBlocking { layoutInspectorRule.inspectorClient.stopFetching() }
  }

  @Test
  fun testFocusableActionButtons() {
    val toolbar = createEmbeddedToolbar()
    toolbar.component.components.forEach { Truth.assertThat(it.isFocusable).isTrue() }
  }

  @Test
  fun testDeviceSelectionToolbarIsImportant() {
    val toolbar = createEmbeddedToolbar()
    val isImportant = toolbar.component.getClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY) as? Boolean ?: false
    assertThat(isImportant).isTrue()
  }

  private fun createEmbeddedToolbar(): ActionToolbar {
    val fakeAction = FakeAction("fake action")
    val toolbarPanel =
      createLayoutInspectorToolbar(androidProjectRule.testRootDisposable, JPanel(), layoutInspectorRule.inspector, fakeAction)

    val toolbars =
      toolbarPanel.allChildren().filterIsInstance<ActionToolbar>().filter { it.component.name == LAYOUT_INSPECTOR_MAIN_TOOLBAR }

    assertThat(toolbars).hasSize(1)
    val toolbar = toolbars.first()

    FakeUi(toolbarPanel, createFakeWindow = true, parentDisposable = androidProjectRule.testRootDisposable)

    waitForCondition(5.seconds) { toolbar.component.components.isNotEmpty() }
    return toolbar
  }
}
