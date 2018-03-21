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
import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint
import com.android.tools.idea.gradle.structure.model.PsIssue
import java.util.*
import java.util.Comparator.comparingInt
import javax.swing.JPanel

class SuggestionsViewer(
    private val context: PsContext,
    private val renderer: IssueRenderer
) : SuggestionsViewerUi() {

  private val groups = mutableListOf<SuggestionGroupViewer>()
  val panel: JPanel get() = myMainPanel

  fun display(issues: List<PsIssue>) {
    val issuesBySeverity = issues.groupBy { it.severity }.toSortedMap(comparingInt { it.priority })
    renderIssues(issuesBySeverity)
    revalidateAndRepaint(panel)
  }

  private fun renderIssues(issues: SortedMap<PsIssue.Severity, List<PsIssue>>) {
    myEmptyIssuesLabel.isVisible = issues.isEmpty()

    for ((groupIndex, group) in groups.withIndex().reversed()) {
      if (!issues.keys.contains(group.severity)) {
        myMainPanel.remove(group.panel)
        groups.removeAt(groupIndex)
      }
    }

    for ((groupIndex, severity) in issues.keys.withIndex()) {
      if (groupIndex >= groups.size || groups[groupIndex].severity != severity) {
        val groupViewerUi = SuggestionGroupViewer(severity)
        groups.add(groupIndex, groupViewerUi)
        myMainPanel.add(groupViewerUi.panel, groupIndex)
      }
      else {
        val group = groups[groupIndex]
        group.view.removeAll()
        myMainPanel.add(group.panel, groupIndex)
      }
    }

    for (group in groups) {
      val groupIssues = issues[group.severity].orEmpty()
      for ((rowIndex, issue) in groupIssues.withIndex()) {
        group.view.add(
            SuggestionViewer(context, renderer, issue, isLast = rowIndex == groupIssues.size - 1).component)
      }
    }
  }
}
