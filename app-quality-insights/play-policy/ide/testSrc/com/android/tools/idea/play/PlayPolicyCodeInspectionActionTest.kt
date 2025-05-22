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
package com.android.tools.idea.play

import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class PlayPolicyCodeInspectionActionTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testDialogContent() = runBlocking {
    enableHeadlessDialogs(projectRule.project)
    val inspectAction = PlayPolicyCodeInspectionAction()
    val mouseEvent = MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, true, 0)
    val event =
      TestActionEvent.createTestEvent(
        inspectAction,
        {
          when (it) {
            CommonDataKeys.PROJECT.name -> projectRule.project
            else -> null
          }
        },
        mouseEvent,
      )
    withContext(Dispatchers.EDT) {
      createModalDialogAndInteractWithIt({ inspectAction.actionPerformed(event) }) { dialog ->
        assertThat(dialog.title).isEqualTo("Specify Inspection Scope")
        assertThat(dialog.isOKActionEnabled).isTrue()
      }
    }
  }
}
