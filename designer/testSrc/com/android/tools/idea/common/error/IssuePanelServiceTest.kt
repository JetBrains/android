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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuePanelServiceTest {

  @JvmField
  @Rule
  val rule = AndroidProjectRule.withAndroidModel().onEdt()

  private lateinit var toolWindow: ToolWindow
  private lateinit var service: IssuePanelService

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

    service = IssuePanelService.getInstance(rule.project)
    service.initIssueTabs(toolWindow)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.clearOverride()
  }

  @Test
  fun testInitWithDesignFile() {
    val file = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    runInEdtAndWait {
      rule.fixture.openFileInEditor(file.virtualFile)
    }

    assertEquals("Layout and Qualifiers", toolWindow.contentManager.selectedContent!!.displayName)
    assertEquals(2, toolWindow.contentManager.contents.size)
  }

  @Test
  fun testInitWithOtherFile() {
    val file = rule.fixture.addFileToProject("/src/file.kt", "")
    runInEdtAndWait {
      rule.fixture.openFileInEditor(file.virtualFile)
    }
    assertEquals("Current File", toolWindow.contentManager.selectedContent!!.displayName)
    assertEquals(1, toolWindow.contentManager.contents.size)
  }

  @Test
  fun testIssuePanelVisibility() {
    val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
    ProblemsView.getToolWindow(rule.project)!!.show()
    runInEdtAndWait {
      rule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    assertFalse(service.isIssuePanelVisible(mock()))

    runInEdtAndWait {
      rule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    assertTrue(service.isIssuePanelVisible(mock()))
  }

  @Test
  fun testGetSharedIssuePanel() {
    val ktFile = rule.fixture.addFileToProject("/src/file.kt", "")
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")

    runInEdtAndWait {
      rule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    assertNull(service.getSelectedSharedIssuePanel())

    runInEdtAndWait {
      rule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    assertNotNull(service.getSelectedSharedIssuePanel())

    runInEdtAndWait {
      rule.fixture.openFileInEditor(ktFile.virtualFile)
    }
    assertNull(service.getSelectedSharedIssuePanel())
  }

  @Test
  fun testSetVisibility() {
    val toolWindow = ToolWindowManager.getInstance(rule.project).getToolWindow(ProblemsView.ID)!!
    toolWindow.hide()
    val service = IssuePanelService.getInstance(rule.project).apply { initIssueTabs(toolWindow) }
    val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")

    runInEdtAndWait {
      rule.fixture.openFileInEditor(layoutFile.virtualFile)
    }
    toolWindow.contentManager.let { it.setSelectedContent(it.contents[0]) } // select current file tab.
    assertNull(service.getSelectedSharedIssuePanel())

    runInEdtAndWait {
      service.setSharedIssuePanelVisibility(true)
    }
    assertTrue(toolWindow.isVisible)

    runInEdtAndWait {
      service.setSharedIssuePanelVisibility(false)
    }
    assertFalse(toolWindow.isVisible)
  }

  @Test
  fun testTabName() {
    rule.projectRule.initAndroid(true)
    val messageBus = rule.project.messageBus
    val toolWindow = ToolWindowManager.getInstance(rule.project).getToolWindow(ProblemsView.ID)!!
    runInEdtAndWait {
      val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "<FrameLayout />")
      rule.fixture.openFileInEditor(layoutFile.virtualFile)
      service.setSharedIssuePanelVisibility(true)
    }

    val content = toolWindow.contentManager.selectedContent!!
    val source = Any()

    // The tab name is updated in EDT thread, we need to wait for updating
    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf())
    }
    assertEquals("Layout and Qualifiers", content.displayName)

    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf(TestIssue()))
    }
    assertEquals("<html><body>Layout and Qualifiers <font color=\"#999999\">1</font></body></html>", content.displayName)

    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf(TestIssue(summary = "1"), TestIssue(summary = "2")))
    }
    assertEquals("<html><body>Layout and Qualifiers <font color=\"#999999\">2</font></body></html>", content.displayName)

    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, listOf())
    }
    assertEquals("Layout and Qualifiers", content.displayName)
  }
}

class TestToolWindowManager(private val project: Project)
  : ToolWindowHeadlessManagerImpl(project) {
  private val idToToolWindow = mutableMapOf<String, ToolWindow>()

  override fun doRegisterToolWindow(id: String): ToolWindow {
    val window = TestToolWindow(project)
    idToToolWindow[id] = window
    return window
  }

  override fun getToolWindow(id: String?): ToolWindow? {
    return idToToolWindow[id]
  }
}

/**
 * This window is used to test the change of availability.
 */
class TestToolWindow(project: Project) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private var _isAvailable = false
  private var _isVisible = false

  override fun setAvailable(available: Boolean, runnable: Runnable?) {
    _isAvailable = available
  }

  override fun setAvailable(value: Boolean) {
    _isAvailable = value
  }

  override fun isAvailable() = _isAvailable

  override fun isVisible() = _isVisible

  override fun show() {
    show(null)
  }

  override fun show(runnable: Runnable?) {
    _isVisible = true
    runnable?.run()
  }

  override fun hide() {
    hide(null)
  }

  override fun hide(runnable: Runnable?) {
    _isVisible = false
    runnable?.run()
  }

  override fun isDisposed(): Boolean {
    return contentManager.isDisposed
  }
}
