/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList

class VisualLintIssueProvider(
  private val sourceModel: NlModel,
  private val renderErrorModel: RenderErrorModel,
  private val atfIssues: ImmutableList<VisualLintAtfIssue>
) : IssueProvider() {

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    renderErrorModel.issues.forEach { issue: RenderErrorModel.Issue ->
      issueListBuilder.add(VisualLintRenderIssueWrapper(issue, sourceModel))
    }

    atfIssues.forEach { issue -> issueListBuilder.add(issue) }
  }

  class VisualLintRenderIssueWrapper(myIssue: RenderErrorModel.Issue, val sourceModel: NlModel) : Issue(), VisualLintHighlightingIssue {
    override val source = VisualLintIssueSource(sourceModel)
    override val summary = myIssue.summary
    override val description = myIssue.htmlContent
    override val severity = myIssue.severity
    override val category = "Visual Lint Issue"
    override val hyperlinkListener = myIssue.hyperlinkListener
    override fun shouldHighlight(model: NlModel): Boolean {
      return sourceModel == model
    }

  }

  data class VisualLintIssueSource(private val model: NlModel) : IssueSource {
    override val displayText: String = model.modelDisplayName.orEmpty()
    override val onIssueSelected: (DesignSurface) -> Unit = {
      // Repaint DesignSurface when issue is selected to update visibility of WarningLayer
      it.repaint()
      it.scrollToVisible(model, false)
    }
  }
}

/** Issue that highlights */
interface VisualLintHighlightingIssue {

  /**
   * return true if the issue should be highlighting when selected.
   * @param model Currently displaying model.
   * */
  fun shouldHighlight(model: NlModel): Boolean
}