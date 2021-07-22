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
package com.android.tools.adtui.toolwindow.splittingtabs.state

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JLabel
import javax.swing.JPanel

class SplittingTabsStateManagerTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val stateManager = SplittingTabsStateManager()

  @Test
  fun getState_clientsWithState() {
    stateManager.registerToolWindow(createToolWindowWithStates(
      "toolWindow1", 1, ContentInfo("tab-1-1", "state-1-1"), ContentInfo("tab-1-2", "state-1-2"),
    ))
    stateManager.registerToolWindow(createToolWindowWithStates(
      "toolWindow2", 0, ContentInfo("tab-2-1", "state-2-1"), ContentInfo("tab-2-2", "state-2-2"),
    ))

    val state = stateManager.state

    assertThat(state.toolWindows).containsExactly(
      ToolWindowState("toolWindow1", listOf(TabState("tab-1-1", "state-1-1"), TabState("tab-1-2", "state-1-2")), selectedTabIndex = 1),
      ToolWindowState("toolWindow2", listOf(TabState("tab-2-1", "state-2-1"), TabState("tab-2-2", "state-2-2")), selectedTabIndex = 0),
    )
  }

  @Test
  fun getState_clientsWithoutState() {
    stateManager.registerToolWindow(createToolWindowWithoutStates("toolWindow1", 1, "tab-1-1", "tab-1-2"))
    stateManager.registerToolWindow(createToolWindowWithoutStates("toolWindow2", 0, "tab-2-1", "tab-2-2"))

    val state = stateManager.state

    assertThat(state.toolWindows).containsExactly(
      ToolWindowState("toolWindow1", listOf(TabState("tab-1-1", null), TabState("tab-1-2", null)), selectedTabIndex = 1),
      ToolWindowState("toolWindow2", listOf(TabState("tab-2-1", null), TabState("tab-2-2", null)), selectedTabIndex = 0),
    )
  }

  @Test
  fun loadState() {
    val toolWindow1State = ToolWindowState(
      "toolWindow1", listOf(TabState("tab-1-1", "state-1-1"), TabState("tab-1-2", "state-1-2")), selectedTabIndex = 1)
    val toolWindow2State = ToolWindowState(
      "toolWindow2", listOf(TabState("tab-2-1", "state-2-1"), TabState("tab-2-2", "state-2-2")), selectedTabIndex = 0)
    stateManager.loadState(SplittingTabsState(listOf(toolWindow1State, toolWindow2State)))

    assertThat(stateManager.getToolWindowState("toolWindow1")).isEqualTo(toolWindow1State)
    assertThat(stateManager.getToolWindowState("toolWindow2")).isEqualTo(toolWindow2State)
  }

  private fun createToolWindowWithStates(id: String, selectedIndex: Int, vararg contents: ContentInfo): FakeToolWindow {
    return FakeToolWindow(projectRule.project, id).apply {
      val factory = contentManager.factory
      contents.forEach { contentManager.addContent(factory.createContent(JLabelWithState(it.clientState), it.tabName, false)) }
      contentManager.setSelectedContent(contentManager.contents[selectedIndex])
    }
  }

  private fun createToolWindowWithoutStates(id: String, selectedIndex: Int, vararg tabNames: String): FakeToolWindow {
    return FakeToolWindow(projectRule.project, id).apply {
      val factory = contentManager.factory
      tabNames.forEach { contentManager.addContent(factory.createContent(JPanel(), it, false)) }
      contentManager.setSelectedContent(contentManager.contents[selectedIndex])
    }
  }

  private class FakeToolWindow(project: Project, val toolWindowId: String) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    override fun getId(): String = toolWindowId
  }

  private data class ContentInfo(val tabName: String, val clientState: String)

  private class JLabelWithState(val clientState: String) : JLabel(clientState), SplittingTabsStateProvider {
    override fun getState(): String = clientState
  }
}