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
package com.android.tools.idea.compose.preview.util

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.compose.preview.ComposeStudioBotActionFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup

open class FakeStudioBotActionFactory : ComposeStudioBotActionFactory {

  private fun fakeAction(text: String): AnAction {
    return object : AnAction(text) {
      override fun actionPerformed(e: AnActionEvent) {}
    }
  }

  private fun fakeDefaultActionGroup(text: String): DefaultActionGroup {
    return DefaultActionGroup(text, listOf(fakeAction(text)))
  }

  private fun fakeDropDownAction(text: String): DropDownAction {
    return object : DropDownAction(text, null, null) {
      init {
        add(fakeAction(text))
      }
    }
  }

  override fun createPreviewGenerator(): AnAction? = fakeAction("previewGenerator")

  override fun transformPreviewAction() = fakeAction("transformPreview")

  override fun fixVisualLintIssuesAction(methodFqn: String) = fakeAction("fixVisualLintIssues")

  override fun fixComposeRenderIssueAction() = fakeAction("fixComposeRender")

  override fun previewAgentsDropDownAction(): DropDownAction = fakeDropDownAction("previewAgents")

  override fun previewAgentsActionGroup() = fakeDefaultActionGroup("previewAgents")

  override fun screenshotToCodeAction(): AnAction {
    return fakeAction("Generate Code From Screenshot")
  }

  override fun previewAgentsToolbarAction(): DropDownAction? =
    fakeDropDownAction("previewAgentsToolbar")
}
