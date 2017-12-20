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
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeAndTextComparator
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsModule
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place

class SuggestionsForm(
    private val context: PsContext
) : SuggestionsFormUi(), Disposable {

  val panel = myMainPanel!!

  private val issuesViewer = SuggestionsViewer(context, SuggestionsViewIssueRenderer(context))

  init {
    setViewComponent(issuesViewer.panel)
    renderIssues(listOf())

    context.project.forEachModule { module ->
      module.add(PsModule.DependenciesChangeListener { dependencyChanged() }, this)
    }
  }

  private fun dependencyChanged() {
    context.analyzerDaemon.recreateUpdateIssues()
    analyzeProject()
  }

  private fun analyzeProject() {
    val daemon = context.analyzerDaemon
    daemon.removeIssues(PROJECT_ANALYSIS)
    context.project.forEachModule({ daemon.queueCheck(it) })
    updateLoading()
  }

  private fun updateLoading() {
    myLoadingLabel.isVisible = context.analyzerDaemon.isRunning
  }

  internal fun renderIssues(issues: List<PsIssue>) {
    if (Disposer.isDisposed(this)) return
    updateLoading()
    issuesViewer.display(issues.sortedWith(IssuesByTypeAndTextComparator.INSTANCE))
  }

  override fun dispose() {}

  fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = ActionCallback.DONE

  fun queryPlace(place: Place) {}
}
