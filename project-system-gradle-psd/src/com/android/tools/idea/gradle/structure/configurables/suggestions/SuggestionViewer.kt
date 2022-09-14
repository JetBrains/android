// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.suggestions

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.IssueRenderer
import com.android.tools.idea.gradle.structure.configurables.issues.NavigationHyperlinkListener
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

class SuggestionViewer(
    private val context: PsContext,
    private val renderer: IssueRenderer,
    issue: PsIssue,
    scope: PsPath?,
    isLast: Boolean
) : SuggestionViewerUi(isLast) {

  val component: Component get() = myPanel
  private val hyperlinkListener = NavigationHyperlinkListener(context)

  init {
    renderIssue(issue, scope)
  }

  // Private. Viewers cannot be reused.
  private fun renderIssue(issue: PsIssue, scope: PsPath?) {
    myText.text = renderer.renderIssue(issue, scope)
    myIconLabel.icon = issue.severity.icon
    myText.addHyperlinkListener(hyperlinkListener)

    myUpdateButton.isVisible = issue.quickFixes.isNotEmpty()

    myUpdateButton.action = issue.quickFixes.firstOrNull()?.toAction()
    myUpdateButton.options = issue.quickFixes.drop(1).mapNotNull { it.toAction() }.toTypedArray()
  }

  private fun PsQuickFix.toAction(): Action = action(text) { execute(context) }
}

private fun action(text: String, handler: () -> Unit): Action =
  object : AbstractAction(text) {
    override fun actionPerformed(e: ActionEvent?) = handler()
  }

