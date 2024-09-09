/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InsightBottomPanelTest {

  private val projectRule = ProjectRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(EdtRule()).around(projectRule)
  private var isContextProvided = false

  private val stubStudioBot =
    object : StudioBot.StubStudioBot() {
      override fun isContextAllowed(project: Project) = isContextProvided
    }

  @Before
  fun setup() {
    application.replaceService(StudioBot::class.java, stubStudioBot, projectRule.disposable)
  }

  @Test
  fun `test enable code context visibility`() = runBlocking {
    val bottomPanel = InsightBottomPanel()

    val fakeUi = FakeUi(bottomPanel)
    val toolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place == "InsightBottomPanelLeftToolBar" }
        ?: fail("Toolbar not found")
    assertThat(toolbar.actions.size).isEqualTo(2)
    val codeContextAction = toolbar.actions[0]
    val testEvent =
      TestActionEvent.createTestEvent {
        when {
          CommonDataKeys.PROJECT.`is`(it) -> projectRule.project
          else -> null
        }
      }

    codeContextAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isTrue()

    isContextProvided = true

    codeContextAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun `test copy action`() = runBlocking {
    val bottomPanel = InsightBottomPanel()

    val fakeUi = FakeUi(bottomPanel)
    val toolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place == "InsightBottomPanelRightToolBar" }
        ?: fail("Toolbar not found")
    assertThat(toolbar.actions.size).isEqualTo(2)
    val copyAction = toolbar.actions[0]
    val copyProvider =
      object : CopyProvider {
        var text = ""

        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun performCopy(dataContext: DataContext) = Unit

        override fun isCopyEnabled(dataContext: DataContext) = text.isNotBlank()

        override fun isCopyVisible(dataContext: DataContext) = true
      }
    val testEvent =
      TestActionEvent.createTestEvent {
        when {
          PlatformDataKeys.COPY_PROVIDER.`is`(it) -> copyProvider
          else -> null
        }
      }

    CopyPasteManager.copyTextToClipboard("default text")

    copyAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabled).isFalse()
    assertThat(testEvent.presentation.isVisible).isTrue()

    copyProvider.text = "interesting insight"

    copyAction.update(testEvent)
    assertThat(testEvent.presentation.isEnabled).isTrue()
    assertThat(testEvent.presentation.isVisible).isTrue()
  }
}
