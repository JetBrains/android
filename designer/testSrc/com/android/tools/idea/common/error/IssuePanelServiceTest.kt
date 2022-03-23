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

import com.android.tools.idea.common.surface.TestDesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodeEditorProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ProjectRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuePanelServiceTest {

  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @JvmField
  @Rule
  val ideaRule = ProjectRule()

  private lateinit var toolWindow: ToolWindow

  @Before
  fun setup() {
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.override(true)

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

  @Test
  fun testInitWithDesignFile() {
    runInEdt {
      val file = rule.fixture.addFileToProject("/res/layout/layout.xml", "")
      rule.fixture.openFileInEditor(file.virtualFile)

      FileEditorManager.getInstance(rule.project).setSelectedEditor(file.virtualFile, SourceCodeEditorProvider().editorTypeId)
      IssuePanelService.getInstance(rule.project).initIssueTabs(toolWindow)
      assertTrue(toolWindow.contentManager.selectedContent!!.displayName == "Layout And Qualifiers")
      assertTrue(toolWindow.contentManager.contents.size == 2)
    }
  }

  @Test
  fun testInitWithOtherFile() {
    runInEdt {
      val file = rule.fixture.addFileToProject("/src/file.kt", "")

      FileEditorManager.getInstance(rule.project).setSelectedEditor(file.virtualFile, TextEditorProvider().editorTypeId)
      IssuePanelService.getInstance(rule.project).initIssueTabs(toolWindow)
      assertTrue(toolWindow.contentManager.selectedContent!!.displayName == "Current File")
      assertTrue(toolWindow.contentManager.contents.size == 1)
    }
  }

  @Test
  fun testIssuePanelVisibility() {
    runInEdt {
      val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
      val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "")

      FileEditorManager.getInstance(rule.project).setSelectedEditor(ktFile.virtualFile, TextEditorProvider().editorTypeId)

      val service = IssuePanelService.getInstance(rule.project)
      val surface = TestDesignSurface(rule.project, rule.testRootDisposable)
      assertFalse(service.isIssuePanelVisible(surface))

      FileEditorManager.getInstance(rule.project).setSelectedEditor(layoutFile.virtualFile, SourceCodeEditorProvider().editorTypeId)
      assertTrue(service.isIssuePanelVisible(surface))
    }
  }

  @Test
  fun testGetSharedIssuePanel() {
    runInEdt {
      val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
      val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "")

      FileEditorManager.getInstance(rule.project).setSelectedEditor(ktFile.virtualFile, TextEditorProvider().editorTypeId)

      val service = IssuePanelService.getInstance(rule.project)
      assertNull(service.getSelectedSharedIssuePanel())

      FileEditorManager.getInstance(rule.project).setSelectedEditor(layoutFile.virtualFile, SourceCodeEditorProvider().editorTypeId)
      assertNotNull(service.getSelectedSharedIssuePanel())
    }
  }
}
