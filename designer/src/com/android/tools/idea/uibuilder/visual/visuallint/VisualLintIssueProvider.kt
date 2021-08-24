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
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.xml.XmlChildRole

class VisualLintIssueProvider(
  private val issuesMap: VisualLintIssues,
) : IssueProvider() {

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    issueListBuilder.addAll(issuesMap.list)
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

  /** Number of times the same issue appears */
  var count: Int = 0

  /** Returns the text range of the issue. */
  private var range: TextRange? = null

  init {
    updateRange()
  }

  private fun updateRange() {
    source.components.forEach { component ->
      component.let {
        range = getTextRange(it)
        return@forEach
      }
    }
  }

  private fun getTextRange(component: NlComponent): TextRange? {
    component.tag?.let { tag ->
      val nameElement: ASTNode? = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.node)
      return nameElement?.textRange
    }
    return null
  }

  /** Hash code that depends on xml range rather than component. */
  fun rangeBasedHashCode(): Int {
    var result = 13
    result += 17 * severity.hashCode()
    result += 19 * summary.hashCode()
    result += 23 * description.hashCode()
    result += 29 * category.hashCode()
    result += 31 * range.hashCode()
    return result
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