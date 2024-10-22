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
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.Content
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tests for [MoveTabAction]
 */
class MoveTabActionTest {
  //@get:Rule
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val moveLeftAction = MoveTabAction.Left()
  private val moveRightAction = MoveTabAction.Right()
  private val toolWindow  by lazy { ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project)}
  private val content1 by lazy { createContent(toolWindow) }
  private val content2 by lazy { createContent(toolWindow) }
  private val content3 by lazy { createContent(toolWindow) }

  @Test
  fun presentation() {
    assertThat(moveLeftAction.templatePresentation.text).isEqualTo("Move Tab Left")
    assertThat(moveLeftAction.templatePresentation.icon).isNull()
    assertThat(moveRightAction.templatePresentation.text).isEqualTo("Move Tab Right")
    assertThat(moveRightAction.templatePresentation.icon).isNull()
  }

  @Test
  fun update_actionEnabledWhenAvailable() {
    toolWindow.contentManager.addContent(content1)
    toolWindow.contentManager.addContent(content2)
    toolWindow.contentManager.addContent(content3)

    assertActionsEnabledState(content1, leftEnabled = false, rightEnabled = true)
    assertActionsEnabledState(content2, leftEnabled = true, rightEnabled = true)
    assertActionsEnabledState(content3, leftEnabled = true, rightEnabled = false)
  }

  @Test
  fun moveLeft_unchangedIfUnavailable() {
    toolWindow.contentManager.addContent(content1)
    toolWindow.contentManager.addContent(content2)
    toolWindow.contentManager.addContent(content3)

    moveLeftAction.actionPerformed(content1)

    assertThat(toolWindow.contentManager.contents).asList().containsExactly(content1, content2, content3).inOrder()
  }

  @Test
  fun moveRight_unchangedIfUnavailable() {
    toolWindow.contentManager.addContent(content1)
    toolWindow.contentManager.addContent(content2)
    toolWindow.contentManager.addContent(content3)

    moveRightAction.actionPerformed(content3)

    assertThat(toolWindow.contentManager.contents).asList().containsExactly(content1, content2, content3).inOrder()
  }

  @Test
  fun moveLeft() {
    toolWindow.contentManager.addContent(content1)
    toolWindow.contentManager.addContent(content2)
    toolWindow.contentManager.addContent(content3)

    moveLeftAction.actionPerformed(content2)

    assertThat(toolWindow.contentManager.contents).asList().containsExactly(content2, content1, content3).inOrder()
  }

  @Test
  fun moveRight() {
    toolWindow.contentManager.addContent(content1)
    toolWindow.contentManager.addContent(content2)
    toolWindow.contentManager.addContent(content3)

    moveRightAction.actionPerformed(content2)

    assertThat(toolWindow.contentManager.contents).asList().containsExactly(content1, content3, content2).inOrder()
  }

  private fun assertActionsEnabledState(content: Content, leftEnabled: Boolean, rightEnabled: Boolean) {
    val presentation = Presentation()
    assertThat(presentation.isVisible).isTrue()
    assertThat(moveLeftAction.isEnabled(content)).isEqualTo(leftEnabled)
    moveRightAction.isEnabled(content)
    assertThat(presentation.isVisible).isTrue()
    assertThat(moveRightAction.isEnabled(content)).isEqualTo(rightEnabled)
  }

  private fun createContent(toolWindow: ToolWindowHeadlessManagerImpl.MockToolWindow) =
    toolWindow.contentManager.factory.createContent(null, "Content", false).also {
      it.component = SplittingPanel(it, null, object : ChildComponentFactory {
        override fun createChildComponent(state: String?, popupActionGroup: DefaultActionGroup): JComponent = JPanel()
      })
    }
}