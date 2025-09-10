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

import com.google.common.truth.Truth.assertThat
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.actions.CutAction
import com.intellij.ide.actions.PasteAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.ProjectRule
import com.intellij.xdebugger.impl.actions.PauseAction
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class AppInsightsConsoleActionsPostProcessorTest {

  @get:Rule val projectRule = ProjectRule()

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
    val processedActions = processor.postProcessPopupActions(mock(), arrayOf(actions))

    assertThat(processedActions.size).isEqualTo(1)
    assertThat(processedActions.first()).isInstanceOf(CopyAction::class.java)
  }

  private fun createActionGroup(vararg action: AnAction) = DefaultActionGroup(*action)
}
