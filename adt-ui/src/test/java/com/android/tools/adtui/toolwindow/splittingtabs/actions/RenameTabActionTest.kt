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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.android.tools.adtui.toolwindow.splittingtabs.ChildComponentFactory
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests for [RenameTabAction]
 */
class RenameTabActionTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val toolWindow by lazy { ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)}
  private val contentFactory by lazy { toolWindow.contentManager.factory }
  private val event by lazy { TestActionEvent.createTestEvent(
    action, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, projectRule.project).build()) }

  private val action = RenameTabAction()

  @Test
  fun presentationTextSet() {
    assertThat(action.templatePresentation.text).isEqualTo("Rename Tab")
  }

  @Test
  fun update_nullContent_invisible() {
    action.update(event, toolWindow, null)

    assertThat(event.presentation.isVisible).isFalse()
  }


  @Test
  fun update_notSplittingTabContent_invisible() {
    val content = contentFactory.createContent(JPanel(), "Content", false)

    action.update(event, toolWindow, content)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_splittingTabContent_visible() {
    val content = contentFactory.createContent(null, "Content", false).also {
      it.component = SplittingPanel(it, null, object : ChildComponentFactory {
        override fun createChildComponent(state: String?, popupActionGroup: DefaultActionGroup): JComponent = JPanel()
      })
    }
    Disposer.register(toolWindow.contentManager, content)

    action.update(event, toolWindow, content)

    assertThat(event.presentation.isVisible).isTrue()
  }
}
