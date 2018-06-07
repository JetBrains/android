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
import java.awt.Component

class SuggestionViewer(
    private val context: PsContext,
    private val renderer: IssueRenderer,
    issue: PsIssue,
    isLast: Boolean
) : SuggestionViewerUi(isLast) {

  val component: Component get() = myPanel

  init {
    renderIssue(issue)
  }

  // Private. Viewers cannot be reused.
  private fun renderIssue(issue: PsIssue) {
    val hyperlinkListener = NavigationHyperlinkListener(context)
    myText.text = renderer.renderIssue(issue)
    myText.addHyperlinkListener(hyperlinkListener)

    val hyperlinkDestination = issue.quickFixPath?.getHyperlinkDestination(context)
    if (hyperlinkDestination != null) {
      myUpdateButton.addActionListener { _ -> hyperlinkListener.navigate(hyperlinkDestination) }
    }
    else {
      myUpdateButton.isVisible = false
    }
  }
}
