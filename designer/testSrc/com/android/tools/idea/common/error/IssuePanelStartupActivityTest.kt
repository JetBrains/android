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
package com.android.tools.idea.common.error

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class IssuePanelStartupActivityTest {
  @JvmField
  @Rule
  val rule = AndroidProjectRule.withAndroidModel().onEdt()
  private lateinit var toolWindow: ToolWindow

  @Before
  fun setup() {
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.override(true)

    rule.projectRule.replaceProjectService(ToolWindowManager::class.java, TestToolWindowManager(rule.project))
    val manager = ToolWindowManager.getInstance(rule.project)
    toolWindow = manager.registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(null, "Current File", true).apply {
      isCloseable = false
    }
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.clearOverride()
  }

  /**
   * Regression test for b/235316289.
   */
  @RunsInEdt
  @Test
  fun testHavingIssuePanelEvenThereIsNoDesignSurface() {
    var called = 0
    rule.project.messageBus.connect().subscribe(IssueProviderListener.TOPIC, IssueProviderListener { _, _ -> called++ })

    // Before calling IssuePanelStartupActivity().setupIssuePanel(), there is only "Current File" tab.
    assertEquals(1, toolWindow.contentManager.contentCount)

    IssuePanelStartupActivity().setupIssuePanel(rule.project)

    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    rule.fixture.openFileInEditor(layoutFile.virtualFile)

    // The instance of IssuePanelService should be setup already because of IssuePanelStartupActivity.
    assertEquals(2, toolWindow.contentManager.contentCount)
    assertEquals("Layout and Qualifiers".toTabTitle(), toolWindow.contentManager.getContent(1)!!.displayName)

    // Verify the issue panel exists even there is no IssueModel created.
    assertEquals(0, called)
  }
}
