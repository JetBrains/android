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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.google.common.collect.ImmutableCollection

class VisualLintIssueProvider(
  private val issuesMap: MutableMap<VisualLintErrorType, MutableMap<String, MutableList<Issue>>>,
) : IssueProvider() {

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    issuesMap.values.forEach { it.values.forEach { issues -> issueListBuilder.addAll(issues) } }
  }

  data class VisualLintIssueSource(val model: NlModel, val components: List<NlComponent>) : IssueSource {
    override val displayText = ""
  }
}

class VisualLintRenderIssue(myIssue: RenderErrorModel.Issue,
                            val sourceModel: NlModel,
                            val components: MutableList<NlComponent>) : Issue(), VisualLintHighlightingIssue {
  override val source = VisualLintIssueProvider.VisualLintIssueSource(sourceModel, components)
  override val summary = myIssue.summary
  override val description = myIssue.htmlContent
  override val severity = myIssue.severity
  override val category = "Visual Lint Issue"
  override val hyperlinkListener = myIssue.hyperlinkListener
  override fun shouldHighlight(model: NlModel): Boolean {
    return sourceModel == model || components.any { it.model == model }
  }

  fun addComponent(component: NlComponent?) {
    component?.let { components.add(it) }
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