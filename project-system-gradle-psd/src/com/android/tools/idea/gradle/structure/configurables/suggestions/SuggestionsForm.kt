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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesBySeverityPathTextComparator
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place

class SuggestionsForm(
    private val context: PsContext,
    suggestionsViewIssueRenderer: SuggestionsViewIssueRenderer
) : SuggestionsFormUi(), Disposable {
  private val maxNumberOfIssuesToShow = 300
  private val logger = Logger.getInstance(this::class.java)

  val panel = myMainPanel!!

  private val issuesViewer = SuggestionsViewer(context, suggestionsViewIssueRenderer).also {
    Disposer.register(this, it)
  }

  init {
    setViewComponent(issuesViewer.panel)
    renderIssues(listOf(), scope = null)
  }

  @UiThread
  fun updateLoading() {
    myLoadingLabel.isVisible = (context.analyzerDaemon.isRunning || context.libraryUpdateCheckerDaemon.isRunning)
  }

  internal fun renderIssues(issues: List<PsIssue>, scope: PsPath?) {
    if (Disposer.isDisposed(this)) return
    if (issues.size > maxNumberOfIssuesToShow) {
      logger.warn("There are ${issues.size} issues but only $maxNumberOfIssuesToShow will be shown in PSD")
    }
    issuesViewer.display(issues.sortedWith(IssuesBySeverityPathTextComparator.INSTANCE).take(maxNumberOfIssuesToShow), scope)
    updateLoading()
  }

  override fun dispose() {}

  fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = ActionCallback.DONE

  fun queryPlace(place: Place) {}
}
