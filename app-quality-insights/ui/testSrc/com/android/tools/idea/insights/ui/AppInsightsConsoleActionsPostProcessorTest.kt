/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.actions.CutAction
import com.intellij.ide.actions.PasteAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.xdebugger.impl.actions.PauseAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class AppInsightsConsoleActionsPostProcessorTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var stackTraceConsoleView: StackTraceConsoleView

  @Before
  fun setup() {
    stackTraceConsoleView =
      StackTraceConsoleBuilder(projectRule.project).console as StackTraceConsoleView
    Disposer.register(projectRule.disposable, stackTraceConsoleView)
  }

  @Test
  fun testActionsAreFiltered() {
    val actions =
      createActionGroup(
        ClearConsoleAction(),
        createActionGroup(
          CutAction(),
          createActionGroup(PasteAction(), createActionGroup(PauseAction(), CopyAction())),
        ),
      )

    val processor = AppInsightsConsoleActionsPostProcessor()
    val processedActions =
      processor.postProcessPopupActions(stackTraceConsoleView, arrayOf(actions))

    assertThat(processedActions.size).isEqualTo(1)
    assertThat(processedActions.first()).isInstanceOf(CopyAction::class.java)
  }

  @Test
  fun testActionsFilteredOnlyForStackTraceConsoleView() {
    val actions = createActionGroup(ClearConsoleAction(), CutAction(), CopyAction())

    val processor = AppInsightsConsoleActionsPostProcessor()
    // Test with generic ConsoleViewImpl
    val consoleActions =
      processor.postProcessPopupActions(mock<ConsoleViewImpl>(), arrayOf(actions))

    assertThat(consoleActions.size).isEqualTo(1)
    assertThat(consoleActions.first()).isInstanceOf(DefaultActionGroup::class.java)
    assertThat((consoleActions.first() as DefaultActionGroup).getChildren(null).size).isEqualTo(3)

    // Test with StackTraceConsoleView
    val stackTraceConsoleActions =
      processor.postProcessPopupActions(stackTraceConsoleView, arrayOf(actions))
    assertThat(stackTraceConsoleActions.size).isEqualTo(1)
    assertThat(stackTraceConsoleActions.first()).isInstanceOf(CopyAction::class.java)
  }

  private fun createActionGroup(vararg action: AnAction) = DefaultActionGroup(*action)
}
