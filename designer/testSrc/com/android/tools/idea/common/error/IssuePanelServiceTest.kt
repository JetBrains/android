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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.TestToolWindow
import com.android.tools.idea.util.TestToolWindowManager
import com.intellij.analysis.problemsView.toolWindow.HighlightingPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class IssuePanelServiceTest {

  @JvmField @Rule val rule = AndroidProjectRule.withAndroidModel().onEdt()

  private lateinit var toolWindow: ToolWindow
  private lateinit var service: IssuePanelService

  @Before
  fun setup() {
    rule.projectRule.replaceProjectService(
      ToolWindowManager::class.java,
      TestToolWindowManager(rule.project)
    )
    val manager = ToolWindowManager.getInstance(rule.project)
    toolWindow = manager.registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))
    val contentManager = toolWindow.contentManager
    val content =
      contentManager.factory
        .createContent(TestContentComponent(HighlightingPanel.ID), "Current File", true)
        .apply { isCloseable = false }
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    runInEdtAndWait {
      service = IssuePanelService.getInstance(rule.project)
      service.initIssueTabs(toolWindow)
    }
  }

  @Test
  fun testInitWithOtherFile() {
    val file = rule.fixture.addFileToProject("/src/file.kt", "")
    runInEdtAndWait { rule.fixture.openFileInEditor(file.virtualFile) }
    assertEquals("Current File", toolWindow.contentManager.selectedContent!!.displayName)
    assertEquals(1, toolWindow.contentManager.contents.size)
  }

  @Test
  fun testIssuePanelVisibilityWhenOpeningDesignFile() {
    val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    ProblemsView.getToolWindow(rule.project)!!.show()
    runInEdtAndWait { rule.fixture.openFileInEditor(ktFile.virtualFile) }
    assertFalse(service.isIssuePanelVisible())

    runInEdtAndWait { rule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertFalse(service.isIssuePanelVisible())
  }

  @Test
  fun testIssuePanelVisibilityWhenSwitchingToDesignFile() {
    val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    val contentManager = toolWindow.apply { show() }.contentManager

    runInEdtAndWait {
      rule.fixture.openFileInEditor(layoutFile.virtualFile)
      rule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    // Force select "current file" tab.
    contentManager.setSelectedContent(contentManager.contents[0])
    assertFalse(service.isIssuePanelVisible())

    // Switching to layout file. The selected tab should still be "current file" tab.
    runInEdtAndWait { rule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertEquals(contentManager.selectedContent, contentManager.contents[0])
    assertFalse(service.isIssuePanelVisible())
  }

  @Test
  fun testOpeningFileDoesNotOpenSharedIssuePanel() {
    val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    val window = toolWindow as TestToolWindow
    val contentManager = window.contentManager

    runInEdtAndWait { rule.fixture.openFileInEditor(ktFile.virtualFile) }
    assertFalse(
      service.getTabCategory(contentManager.selectedContent!!) ==
        IssuePanelService.TabCategory.DESIGN_TOOLS
    )

    runInEdtAndWait { rule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertFalse(
      service.getTabCategory(contentManager.selectedContent!!) ==
        IssuePanelService.TabCategory.DESIGN_TOOLS
    )

    runInEdtAndWait { rule.fixture.openFileInEditor(ktFile.virtualFile) }
    assertFalse(
      service.getTabCategory(contentManager.selectedContent!!) ==
        IssuePanelService.TabCategory.DESIGN_TOOLS
    )
  }

  @Test
  fun testSetVisibility() {
    val toolWindow = ToolWindowManager.getInstance(rule.project).getToolWindow(ProblemsView.ID)!!
    toolWindow.hide()
    val service =
      IssuePanelService.getInstance(rule.project).apply {
        runInEdtAndWait { initIssueTabs(toolWindow) }
      }
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")

    runInEdtAndWait { rule.fixture.openFileInEditor(layoutFile.virtualFile) }
    toolWindow.contentManager.let {
      it.setSelectedContent(it.contents[0])
      assertTrue(
        service.getTabCategory(it.selectedContent!!) == IssuePanelService.TabCategory.CURRENT_FILE
      )
    } // select current file tab.

    runInEdtAndWait { service.setSharedIssuePanelVisibility(true) }
    assertTrue(toolWindow.isVisible)

    runInEdtAndWait { service.setSharedIssuePanelVisibility(false) }
    assertFalse(toolWindow.isVisible)
  }

  @Test
  fun testTabName() {
    rule.projectRule.initAndroid(true)
    val messageBus = rule.project.messageBus
    val toolWindow = ToolWindowManager.getInstance(rule.project).getToolWindow(ProblemsView.ID)!!
    val layoutFile = runInEdtAndGet {
      val file =
        rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />").virtualFile
      rule.fixture.openFileInEditor(file)
      service.setSharedIssuePanelVisibility(true)
      file
    }
    val issueSource = IssueSourceWithFile(layoutFile)
    val content = toolWindow.contentManager.selectedContent!!
    val source = Any()

    // The tab name is updated in EDT thread, we need to wait for updating
    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf())
    }

    assertEquals("Layout and Qualifiers".toTabTitle(), content.displayName)

    runInEdtAndWait {
      messageBus
        .syncPublisher(IssueProviderListener.TOPIC)
        .issueUpdated(source, listOf(TestIssue(source = issueSource)))
    }
    assertEquals("Layout and Qualifiers".toTabTitle(1), content.displayName)

    runInEdtAndWait {
      messageBus
        .syncPublisher(IssueProviderListener.TOPIC)
        .issueUpdated(
          source,
          listOf(
            TestIssue(summary = "1", source = issueSource),
            TestIssue(summary = "2", source = issueSource)
          )
        )
    }
    assertEquals("Layout and Qualifiers".toTabTitle(2), content.displayName)

    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf())
    }
    assertEquals("Layout and Qualifiers".toTabTitle(), content.displayName)
  }

  /**
   * Note: This test may fail if
   * [com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel.getName] changes the
   * implementation after merging IDEA. Please disable it and file a bug to fix.
   */
  @Test
  fun testTabNameAndStyleSameAsIntellijProblemsPanel() {
    // Create an IJ's problems panel for comparing the custom tab title and style.
    val panel =
      ProblemsViewPanel(rule.project, "ID_IssuePanelServiceTest", ProblemsViewState()) {
        "Problems"
      }
    Disposer.register(rule.testRootDisposable, panel)

    assertEqualsIgnoreSpaces(panel.getName(0), createTabName("Problems", 0))
    assertEqualsIgnoreSpaces(panel.getName(1), createTabName("Problems", 1))
    assertEqualsIgnoreSpaces(panel.getName(10), createTabName("Problems", 10))
  }

  @RunsInEdt
  @Test
  fun testSelectFirstTabWhenSharedIssuePanelIsRemoved() {
    service.setSharedIssuePanelVisibility(true)
    val contentManager = toolWindow.contentManager
    // Add some tool between "Current File" tab and shared issue panel.

    val factory = contentManager.factory
    repeat(3) {
      val newContent = factory.createContent(JPanel(), "Title", false)
      contentManager.addContent(newContent, 1)
    }

    service.setSharedIssuePanelVisibility(true)
    // It should select the shared issue panel.
    assertEquals(
      contentManager.selectedContent,
      contentManager.findContent("Designer".toTabTitle())
    )

    service.removeSharedIssueTabFromProblemsPanel()
    // It should select the first tab, which is the "Current File" tab.
    assertEquals(contentManager.selectedContent, contentManager.findContent("Current File"))
  }

  @RunsInEdt
  @Test
  fun testRegisterFileName() {
    val randomFile = rule.fixture.addFileToProject("src/TestFile.kt", "")
    val layoutFile = rule.fixture.addFileToProject("res/layout/my_layout.xml", "")

    service.registerFile(randomFile.virtualFile, "My Random Surface")

    FileEditorManager.getInstance(rule.project).openFile(randomFile.virtualFile, true)
    assertEquals("My Random Surface", service.getSharedIssuePanelTabTitle())

    service.unregisterFile(randomFile.virtualFile)
    // No surface is found, return default name.
    assertEquals("Designer", service.getSharedIssuePanelTabTitle())

    FileEditorManager.getInstance(rule.project).openFile(layoutFile.virtualFile, true)
    assertEquals("Layout and Qualifiers", service.getSharedIssuePanelTabTitle())

    // If the registered file has no tab title, the default name would be used.
    service.registerFile(randomFile.virtualFile, null)
    FileEditorManager.getInstance(rule.project).openFile(randomFile.virtualFile, true)
    assertEquals("Designer", service.getSharedIssuePanelTabTitle())
  }

  @RunsInEdt
  @Test
  fun testIssuePanelNotPinnable() {
    service.setSharedIssuePanelVisibility(true)

    val manager = toolWindow.contentManager
    val tab = manager.findContent("Designer".toTabTitle())
    assertFalse(tab.isPinnable)
  }

  @Test
  fun testFocusingIssuePanelWhenVisible() {
    val window = toolWindow as TestToolWindow

    service.setSharedIssuePanelVisibility(true)
    assertFalse(window.isFocused())
    service.focusIssuePanelIfVisible()
    assertTrue(window.isFocused())

    // Hide issue panel will lose the focus because the component is no longer visible.
    service.setSharedIssuePanelVisibility(false)
    assertFalse(window.isFocused())
  }

  @Test
  fun testSetIssuePanelVisibility() {
    val window = toolWindow as TestToolWindow
    val contentManager = window.contentManager
    val additionalContent =
      contentManager.factory.createContent(mock(), "Additional Content", false).apply {
        tabName = "Additional Content"
        isCloseable = false
      }
    window.hide()
    contentManager.setSelectedContent(additionalContent)

    service.setIssuePanelVisibility(true, null)
    assertTrue(window.isVisible)
    assertEquals(additionalContent, window.contentManager.selectedContent)

    service.setIssuePanelVisibility(false, null)
    assertFalse(window.isVisible)
    assertEquals(additionalContent, window.contentManager.selectedContent)

    service.setIssuePanelVisibility(true, IssuePanelService.TabCategory.CURRENT_FILE)
    assertTrue(window.isVisible)
    assertTrue(
      service.getTabCategory(contentManager.selectedContent!!) ==
        IssuePanelService.TabCategory.CURRENT_FILE
    )
  }

  @RunsInEdt
  @Test
  fun testRegisterFile() {
    val file = rule.fixture.addFileToProject("/src/file.kt", "").virtualFile

    service.removeSharedIssueTabFromProblemsPanel()
    assertFalse(service.isSharedIssuePanelAddedToProblemsPane())

    rule.fixture.openFileInEditor(file)
    // The issue panel should be added back after register the file
    service.registerFile(file, null)
    assertTrue(service.isSharedIssuePanelAddedToProblemsPane())
  }

  @Test
  fun testExplicitShowCallSelectsTheCorrectTab() {
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    ProblemsView.getToolWindow(rule.project)!!.show()
    runInEdtAndWait { rule.fixture.openFileInEditor(layoutFile.virtualFile) }
    assertFalse(service.isIssuePanelVisible())

    service.setIssuePanelVisibility(true, IssuePanelService.TabCategory.CURRENT_FILE)
    assertFalse(service.isIssuePanelVisible())
  }
}

private class TestContentComponent(private val id: String) : JComponent(), ProblemsViewTab {
  override fun getName(count: Int): String = id

  override fun getTabId(): String = id
}

/** Asserts [expected] and [actual] are the same string when removing all spaces and new lines. */
fun assertEqualsIgnoreSpaces(expected: String, actual: String) {
  assertEquals(expected.replace("\\s+".toRegex(), ""), actual.replace("\\s+".toRegex(), ""))
}
