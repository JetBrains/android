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
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.ToggleLiveUpdatesAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.impl.model.util.FakeAction
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.util.AndroidBundle
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class LayoutInspectorMainToolbarLegacyDeviceTest {
  @get:Rule val edtRule = EdtRule()

  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val layoutInspectorRule =
    LayoutInspectorRule(
      listOf(LegacyClientProvider({ projectRule.testRootDisposable })),
      projectRule
    )

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(layoutInspectorRule)!!

  @Test
  fun testLiveControlDisabledWithProcessFromLegacyDevice() =
    withEmbeddedLayoutInspector(false) {
      layoutInspectorRule.attachDevice(LEGACY_DEVICE)
      layoutInspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
      waitForCondition(5, TimeUnit.SECONDS) { layoutInspectorRule.inspectorClient.isConnected }

      val toolbar = createToolbar()

      val toggle =
        toolbar.component.components.find {
          it is ActionButton && it.action is ToggleLiveUpdatesAction
        } as ActionButton
      assertThat(toggle.isEnabled).isFalse()
      assertThat(getPresentation(toggle).description)
        .isEqualTo("Live updates not available for devices below API 29")
    }

  @Test
  fun testLiveControlDisabledWithProcessFromModernDevice() =
    withEmbeddedLayoutInspector(false) {
      layoutInspectorRule.launchSynchronously = false
      layoutInspectorRule.startLaunch(1)

      layoutInspectorRule.processes.selectedProcess = MODERN_PROCESS
      waitForCondition(5, TimeUnit.SECONDS) { layoutInspectorRule.inspectorClient.isConnected }

      val toolbar = createToolbar()

      val toggle =
        toolbar.component.components.find {
          it is ActionButton && it.action is ToggleLiveUpdatesAction
        } as ActionButton
      assertThat(toggle.isEnabled).isFalse()
      assertThat(getPresentation(toggle).description)
        .isEqualTo(AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY))
    }

  private fun createToolbar(): ActionToolbar {
    val fakeAction = FakeAction("fake action")
    return createStandaloneLayoutInspectorToolbar(
      projectRule.testRootDisposable,
      JPanel(),
      layoutInspectorRule.inspector,
      fakeAction
    )
  }

  private fun getPresentation(button: ActionButton): Presentation {
    val presentation = Presentation()
    val event =
      AnActionEvent(
        null,
        DataManager.getInstance().getDataContext(button),
        "LayoutInspector.MainToolbar",
        presentation,
        ActionManager.getInstance(),
        0
      )
    button.action.update(event)
    return presentation
  }
}
