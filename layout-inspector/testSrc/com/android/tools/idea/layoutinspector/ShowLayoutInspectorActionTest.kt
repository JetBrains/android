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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ShowLayoutInspectorActionTest {

  private val projectRule = ProjectRule()
  private val applicationRule = ApplicationRule()
  private val disposableRule = DisposableRule()

  @get:Rule val chain = RuleChain(applicationRule, projectRule, disposableRule, EdtRule())

  @Before
  fun setUp() {
    // This line avoids the error: UnindexedFilesScannerExecutorImpl is initialized during dispose
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
  }

  @Test
  fun testActivateLayoutInspectorToolWindow() =
    withEmbeddedLayoutInspector(false) {
      val layoutInspectorToolWindow = createFakeToolWindow(projectRule.project, disposableRule.disposable, LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
      val showLayoutInspectorAction = ShowLayoutInspectorAction()
      assertThat(layoutInspectorToolWindow.isActive).isFalse()
      showLayoutInspectorAction.actionPerformed(createFakeEvent(projectRule.project, showLayoutInspectorAction))
      assertThat(layoutInspectorToolWindow.isActive).isTrue()
    }

  @Test
  fun testActivateRunningDevicesToolWindow() = withEmbeddedLayoutInspector {
    val runningDevicesToolWindow = createFakeToolWindow(projectRule.project, disposableRule.disposable, RUNNING_DEVICES_TOOL_WINDOW_ID)
    val showLayoutInspectorAction = ShowLayoutInspectorAction()
    assertThat(runningDevicesToolWindow.isActive).isFalse()
    showLayoutInspectorAction.actionPerformed(createFakeEvent(projectRule.project, showLayoutInspectorAction))
    assertThat(runningDevicesToolWindow.isActive).isTrue()
  }
}

private fun createFakeEvent(project: Project, anAction: AnAction) =
  createEvent(
    anAction,
    { keyName: String ->
      when (keyName) {
        CommonDataKeys.PROJECT.name -> project
        else -> null
      }
    },
    null,
    "",
    ActionUiKind.NONE,
    null,
  )
