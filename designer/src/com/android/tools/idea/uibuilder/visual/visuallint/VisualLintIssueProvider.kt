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
import com.android.tools.idea.uibuilder.lint.getTextRange
import com.android.utils.HtmlBuilder
import com.google.common.collect.ImmutableCollection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import java.util.Objects
import javax.swing.event.HyperlinkListener
import kotlin.test.assertNotNull

class VisualLintIssueProvider : IssueProvider() {
  private val issues = VisualLintIssues()

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    issueListBuilder.addAll(issues.list)
  }

  fun addIssue(errorType: VisualLintErrorType, issue: Issue) = issues.add(errorType, issue)

  fun addAllIssues(errorType: VisualLintErrorType, issues: List<Issue>) = this.issues.addAll(errorType, issues)

  fun getIssues() = issues.list

  fun clear() = issues.clear()

  data class VisualLintIssueSource(val models: Set<NlModel>, val components: List<NlComponent>) : IssueSource {
    override val displayText = ""
  }
}

/** Lint issues that is generated from visual sources (e.g. Layout Validation) */
class VisualLintRenderIssue private constructor(private val builder: Builder): Issue(), VisualLintHighlightingIssue {
  val models = builder.model?.let { mutableSetOf(it) } ?: mutableSetOf()
  val components = builder.components!!
  override val source = VisualLintIssueProvider.VisualLintIssueSource(models, components)
  override val summary = builder.summary!!
  override val severity = builder.severity!!
  override val category = "Visual Lint Issue"
  override val hyperlinkListener = builder.hyperlinkListener
  override fun shouldHighlight(model: NlModel): Boolean {
    return models.contains(model)
  }
  override val description: String get() = builder.contentDescriptionProvider!!.invoke(models.size).stringBuilder.toString()

  /** Returns the text range of the issue. */
  private var range: TextRange? = null

  init {
    runReadAction {
      updateRange()
    }
  }

  private fun updateRange() {
    source.components.forEach { component ->
      component.let {
        range = getTextRange(it)
        return@forEach
      }
    }
  }

  /** Hash code that depends on xml range rather than component. */
  fun rangeBasedHashCode() = Objects.hash(severity, summary, category, range)

  override fun equals(other: Any?) = other === this

  override fun hashCode() = Objects.hash(severity, summary, category)

  /** Builder for [VisualLintRenderIssue] */
  data class Builder(
    var summary: String? = null,
    var severity: HighlightSeverity? = null,
    var contentDescriptionProvider: ((Int) -> HtmlBuilder)? = null,
    var model: NlModel? = null,
    var components: MutableList<NlComponent>? = null,
    var hyperlinkListener: HyperlinkListener? = null) {

    fun summary(summary: String) = apply { this.summary = summary }
    fun severity(severity: HighlightSeverity) = apply { this.severity = severity }
    fun contentDescriptionProvider(provider: (Int) -> HtmlBuilder) = apply { this.contentDescriptionProvider = provider }
    fun model(model: NlModel) = apply { this.model = model }
    fun components(components: MutableList<NlComponent>) = apply { this.components = components }
    fun hyperlinkListener(hyperlinkListener: HyperlinkListener?) = apply { this.hyperlinkListener = hyperlinkListener }

    fun build(): VisualLintRenderIssue {
      assertNotNull(summary)
      assertNotNull(severity)
      assertNotNull(contentDescriptionProvider)
      assertNotNull(model)
      assertNotNull(components)

      return VisualLintRenderIssue(this)
    }
  }

  companion object {
    fun builder(): Builder {
      return Builder()
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