/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.toolwindow.splittingtabs.actions.NewTabAction
import com.android.tools.adtui.toolwindow.splittingtabs.state.PanelState
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsState
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateManager
import com.android.tools.adtui.toolwindow.splittingtabs.state.TabState
import com.android.tools.adtui.toolwindow.splittingtabs.state.ToolWindowState
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl.MockToolWindow
import com.intellij.ui.content.Content
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import kotlin.test.fail


/**
 * Tests for [SplittingTabsToolWindowFactory]
 */
class SplittingTabsToolWindowFactoryTest {

  @get:Rule
  val projectRule = ProjectRule()

  private val toolWindow by lazy { FakeToolWindow(projectRule.project, "toolWindowId") }
  private val stateManager = SplittingTabsStateManager()

  @Before
  fun setUp() {
    projectRule.project.registerServiceInstance(SplittingTabsStateManager::class.java, stateManager)
  }

  @Test
  fun init_setToHideOnEmptyContent() {
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory()

    splittingTabsToolWindowFactory.init(toolWindow)

    assertThat(toolWindow.hideOnEmpty).isFalse()
  }

  @Test
  fun createToolWindowContent_noState_createsNewTab() {
    val component = JLabel("TabContents")
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory({ "TabName" }, { component })

    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    assertThat(toolWindow.contentManager.contents).asList().hasSize(1)
    val content = toolWindow.contentManager.contents[0]
    assertThat(content.displayName).isEqualTo("TabName")
    assertThat((content.component as SplittingPanel).component).isSameAs(component)
    assertThat(content.isSplittingTab()).isTrue()
  }

  @Test
  fun createToolWindowContent_noState_shouldNotCreateNewTabWhenEmpty_doesNotCreateNewTab() {
    val component = JLabel("TabContents")
    val splittingTabsToolWindowFactory =
      TestSplittingTabsToolWindowFactory({ "TabName" }, { component }, shouldCreateNewTabWhenEmpty = false)

    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    assertThat(toolWindow.contentManager.contents).isEmpty()
  }

  @Test
  fun toolWindowShown_empty_createsNewTab() {
    val component = JLabel("TabContents")
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory({ "TabName" }, { component })
    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)
    toolWindow.contentManager.removeAllContents(true)

    projectRule.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(toolWindow)

    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly("TabName")
  }

  @Test
  fun toolWindowShown_empty_shouldNotCreateNewTabWhenEmpty_doesNotCreateNewTab() {
    val component = JLabel("TabContents")
    val splittingTabsToolWindowFactory =
      TestSplittingTabsToolWindowFactory({ "TabName" }, { component }, shouldCreateNewTabWhenEmpty = false)
    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)
    toolWindow.contentManager.removeAllContents(true)

    projectRule.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(toolWindow)

    assertThat(toolWindow.contentManager.contents).isEmpty()
  }

  @Test
  fun toolWindowShown_notEmpty_doesNotCreateNewTab() {
    val component = JLabel("TabContents")
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory({ "TabName" }, { component })
    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    projectRule.project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(toolWindow)

    assertThat(toolWindow.contentManager.contents.map { it.displayName }).containsExactly("TabName")
  }

  /**
   * A basic restore state test. No split content is used. More comprehensive tests are in SplittingPanelTest
   */
  @Test
  fun createToolWindowContent_withState_restoresState() {
    val toolWindow = FakeToolWindow(projectRule.project, "toolWindowId")
    stateManager.loadState(SplittingTabsState(listOf(
      ToolWindowState("toolWindowId", listOf(
        TabState("Tab1", PanelState("clientState1")),
        TabState("Tab2", PanelState("clientState2")),
      )),
      ToolWindowState("anotherToolWindowId", listOf(
        TabState("Tab3", PanelState("clientState3")),
      )),
    )))
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory({ "TabName" }, ::JLabel)

    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    assertThat(toolWindow.contentManager.contents).asList().hasSize(2)
    assertContent(toolWindow.contentManager.contents[0], "Tab1", "clientState1")
    assertContent(toolWindow.contentManager.contents[1], "Tab2", "clientState2")
  }

  @Test
  fun createToolWindowContent_addsNewTabButton() {
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory()

    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)
    assertThat(toolWindow.tabActionList).hasSize(1)
    assertThat(toolWindow.tabActionList[0]).isInstanceOf(NewTabAction::class.java)
    assertThat(toolWindow.tabActionList[0].templatePresentation.text).isEqualTo("New Tab")
    assertThat(toolWindow.tabActionList[0].shortcutSet.shortcuts).asList()
      .containsExactly(KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), null))
  }

  @Test
  fun createToolWindowContent_newTabButton_addsNewTab() {
    var tabNameCount = 1
    var tabContentCount = 1
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory(
      { "TabName-${tabNameCount++}" },
      { JLabel("TabContents-${tabContentCount++}") })
    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    toolWindow.tabActionList[0].actionPerformed(mock())

    assertThat(toolWindow.contentManager.contents).asList().hasSize(2)
    assertContent(toolWindow.contentManager.contents[0], "TabName-1", "TabContents-1")
    assertContent(toolWindow.contentManager.contents[1], "TabName-2", "TabContents-2")
    assertThat(toolWindow.contentManager.contents[1].isSplittingTab()).isTrue()
  }

  @Test
  fun removeContent_disposesTabComponent() {
    val disposableComponent = DisposableComponent()
    val splittingTabsToolWindowFactory = TestSplittingTabsToolWindowFactory(generateChild = { disposableComponent })
    splittingTabsToolWindowFactory.createToolWindowContent(projectRule.project, toolWindow)

    toolWindow.contentManager.removeContent(toolWindow.contentManager.contents[0], true)

    assertThat(disposableComponent.isDisposed).isTrue()
  }

  private fun assertContent(content: Content, expectedTabName: String, expectedContent: String) {
    val component1 = (content.component as? SplittingPanel)?.component as? JLabel ?: fail("Expected a JLabel object")
    assertThat(content.displayName).isEqualTo(expectedTabName)
    assertThat(component1.text).isEqualTo(expectedContent)
  }

  private class TestSplittingTabsToolWindowFactory(
    val generateName: () -> String = { "" },
    val generateChild: (String?) -> JComponent = ::JLabel,
    val shouldCreateNewTabWhenEmpty: Boolean = true
  ) : SplittingTabsToolWindowFactory() {

    override fun generateTabName(tabNames: Set<String>): String = generateName()

    override fun createChildComponent(project: Project, popupActionGroup: ActionGroup, clientState: String?): JComponent =
      generateChild(clientState)

    override fun shouldCreateNewTabWhenEmpty() = shouldCreateNewTabWhenEmpty
  }

  private class FakeToolWindow(project: Project, val toolWindowId: String) : MockToolWindow(project) {
    var hideOnEmpty: Boolean = false
    var tabActionList: MutableList<AnAction> = mutableListOf()

    override fun setToHideOnEmptyContent(hideOnEmpty: Boolean) {
      this.hideOnEmpty = hideOnEmpty
    }

    override fun isVisible(): Boolean = true

    override fun setTabActions(vararg actions: AnAction) {
      tabActionList.clear()
      tabActionList.addAll(actions)
    }

    override fun getId(): String = toolWindowId
  }

  private class DisposableComponent : JPanel(), Disposable {
    var isDisposed = false

    override fun dispose() {
      isDisposed = true
    }
  }
}