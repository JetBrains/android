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

class VisualLintIssueProvider(private val sourceModel: NlModel, val renderErrorModel: RenderErrorModel) : IssueProvider() {

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    renderErrorModel.issues.forEach { issue: RenderErrorModel.Issue ->
      issueListBuilder.add(VisualLintRenderIssueWrapper(issue, sourceModel))
    }
  }

  class VisualLintRenderIssueWrapper(myIssue: RenderErrorModel.Issue, val sourceModel: NlModel) : Issue() {
    override val source = VisualLintIssueSource(sourceModel)
    override val summary = myIssue.summary
    override val description = myIssue.htmlContent
    override val severity = myIssue.severity
    override val category = "Visual Lint Issue"
    override val hyperlinkListener = myIssue.hyperlinkListener
  }

  data class VisualLintIssueSource(private val model: NlModel) : IssueSource {
    override val displayText: String = model.modelDisplayName.orEmpty()
    override val onIssueSelected: (DesignSurface) -> Unit = {
      // Repaint DesignSurface when issue is selected to update visibility of WarningLayer
      it.repaint()
    }
  }
}