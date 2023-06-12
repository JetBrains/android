/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.utils.HtmlBuilder
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.LeafState
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener

internal data class TestIssue(override val summary: String = "",
                              override val description: String = "",
                              override val severity: HighlightSeverity = HighlightSeverity.WARNING,
                              override val source: IssueSource = EmptyIssueSource,
                              override val category: String = "",
                              val fixList: List<Fix> = emptyList(),
                              override val hyperlinkListener: HyperlinkListener? = null)
  : Issue() {

  override val fixes: Stream<Fix>
    get() = fixList.stream()
}

internal object EmptyIssueSource : IssueSource {
  override val file: VirtualFile? = null
  override val displayText: String = ""
}

internal class IssueSourceWithFile(override val file: VirtualFile, override val displayText: String = "") : IssueSource

internal class DesignerCommonIssueTestProvider(private val issues: List<Issue>) : DesignerCommonIssueProvider<Any> {
  override var viewOptionFilter: DesignerCommonIssueProvider.Filter = EmptyFilter

  override fun getFilteredIssues(): List<Issue> = issues.filter(viewOptionFilter)

  override fun registerUpdateListener(listener: Runnable) = Unit

  override fun dispose() = Unit
}

/**
 * For testing the functions which need the parent node.
 */
internal class CommonIssueTestParentNode(project: Project) : DesignerCommonIssueNode(project, null) {
  override fun updatePresentation(presentation: PresentationData) = Unit

  override fun getName(): String = ""

  override fun getChildren(): List<DesignerCommonIssueNode> = emptyList()

  override fun getLeafState(): LeafState = LeafState.ALWAYS
}

fun createTestVisualLintRenderIssue(type: VisualLintErrorType,
                                    components: List<NlComponent>,
                                    summary: String = "") : VisualLintRenderIssue {
  return VisualLintRenderIssue.builder().model(components.first().model)
    .summary(summary)
    .severity(HighlightSeverity.WARNING)
    .contentDescriptionProvider { HtmlBuilder() }
    .model(components.first().model)
    .components(components.toMutableList())
    .type(type)
    .build()
}

internal fun String.toTabTitle(issueCount: Int = 0): String = createTabName(this, issueCount)
