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

import com.android.testutils.MockitoKt
import com.android.tools.adtui.toolwindow.splittingtabs.ChildComponentFactory
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.After
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests for [RenameTabAction]
 */
class RenameTabActionTest {
  @get:Rule
  val appRule = ApplicationRule()

  private val project = MockitoKt.mock<Project>()
  private val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
  private val contentFactory by lazy { toolWindow.contentManager.factory }
  private val event by lazy { TestActionEvent.createTestEvent(
    action, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()) }

  private val action = RenameTabAction()

  @After
  fun tearDown(){
    Disposer.dispose(project)
  }

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
      Disposer.register(project, it)
      it.component = SplittingPanel(it, null, object : ChildComponentFactory {
        override fun createChildComponent(state: String?, popupActionGroup: ActionGroup): JComponent = JPanel()
      })
    }

    action.update(event, toolWindow, content)

    assertThat(event.presentation.isVisible).isTrue()
  }
}
