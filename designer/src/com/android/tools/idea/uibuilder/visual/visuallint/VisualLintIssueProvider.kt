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
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference
import java.util.Objects
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener

abstract class VisualLintIssueProvider(parentDisposable: Disposable) : IssueProvider(), Disposable {
  private val issues = VisualLintIssues()

  /** If using in UI Check mode, represents the Compose Preview instance being checked. */
  var uiCheckInstanceId: String? = null

  init {
    Disposer.register(parentDisposable, this)
  }

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    issueListBuilder.addAll(issues.list)
  }

  override fun dispose() {
    clear()
  }

  private fun addIssue(issue: VisualLintRenderIssue) {
    customizeIssue(issue)
    this.issues.add(issue)
  }

  fun addAllIssues(issues: List<VisualLintRenderIssue>) = issues.forEach { addIssue(it) }

  fun getIssues() = issues.list

  fun clear() = issues.clear()

  fun getUnsuppressedIssues() = issues.list.filterNot { it.isSuppressed() }

  /** This is applied to all issues added to this [IssueProvider] */
  abstract fun customizeIssue(issue: VisualLintRenderIssue)

  @Suppress("UnstableApiUsage")
  class VisualLintIssueSource(models: Set<NlModel>, components: List<NlComponent>) : IssueSource {
    private val modelRefs = models.map { WeakReference(it) }.toMutableList()
    private val componentRefs = components.map { WeakReference(it) }.toMutableList()

    val models: Set<NlModel>
      get() = synchronized(modelRefs) { modelRefs.mapNotNull { it.get() }.toSet() }

    val components: List<NlComponent>
      get() = synchronized(componentRefs) { componentRefs.mapNotNull { it.get() }.toList() }

    override val files: Set<VirtualFile>
      get() = models.map { BackedVirtualFile.getOriginFileIfBacked(it.virtualFile) }.toSet()

    override val displayText = ""

    fun addComponent(component: NlComponent) {
      synchronized(componentRefs) { componentRefs.add(WeakReference(component)) }
    }

    fun addModel(model: NlModel) {
      synchronized(modelRefs) { modelRefs.add(WeakReference(model)) }
    }
  }
}

/** Lint issues that is generated from visual sources (e.g. Layout Validation) */
class VisualLintRenderIssue private constructor(private val builder: Builder) :
  Issue(), VisualLintHighlightingIssue {
  val models = builder.model?.let { mutableSetOf(it) } ?: mutableSetOf()
  private var isComponentSuppressed: (NlComponent) -> Boolean = { false }
  private val _components = builder.components!!
  private val allComponents
    get() = synchronized(_components) { _components.toList() }

  /** List of [NlComponent]s that have not been suppressed */
  val components
    get() = synchronized(_components) { _components.filterNot(isComponentSuppressed).toList() }

  val type: VisualLintErrorType = builder.type!!
  override val source = VisualLintIssueProvider.VisualLintIssueSource(models, components)
  override val summary = builder.summary!!
  override val severity = builder.severity!!
  override val category = "Visual Lint Issue"
  override val hyperlinkListener = builder.hyperlinkListener

  override fun shouldHighlight(model: NlModel): Boolean {
    return components.map { it.model }.contains(model)
  }

  override val description: String
    get() =
      builder.contentDescriptionProvider!!.invoke(unsuppressedModelCount).stringBuilder.toString()

  /** Returns the text range of the issue. */
  private var range: TextRange? = null

  private val suppressList: MutableList<Suppress> = mutableListOf()

  override val suppresses: Stream<Suppress>
    get() = suppressList.stream()

  init {
    runReadAction { updateRange() }
  }

  private fun updateRange() {
    synchronized(_components) {
      source.components.forEach { component ->
        component.let {
          range = it.getTextRange()
          return@forEach
        }
      }
    }
  }

  /** Hash code that depends on xml range rather than component. */
  fun rangeBasedHashCode() = Objects.hash(severity, summary, category, range)

  override fun equals(other: Any?) = other === this

  override fun hashCode() = Objects.hash(severity, summary, category)

  /** Get the number of [NlModel] which is not suppressed. */
  private val unsuppressedModelCount: Int
    get() = components.map { it.model }.distinct().count()

  fun isSuppressed(): Boolean {
    return components.isEmpty()
  }

  fun addSuppress(suppress: Suppress) {
    suppressList.add(suppress)
  }

  fun customizeIsSuppressed(isComponentSuppressedMethod: (NlComponent) -> Boolean) {
    isComponentSuppressed = isComponentSuppressedMethod
  }

  fun combineWithIssue(issue: VisualLintRenderIssue) {
    synchronized(_components) { _components.addAll(issue.allComponents) }
    models.addAll(issue.models)
    issue.allComponents.forEach { source.addComponent(it) }
    issue.models.forEach { source.addModel(it) }
  }

  /** Builder for [VisualLintRenderIssue] */
  data class Builder(
    var summary: String? = null,
    var severity: HighlightSeverity? = null,
    var contentDescriptionProvider: ((Int) -> HtmlBuilder)? = null,
    var model: NlModel? = null,
    var components: MutableList<NlComponent>? = null,
    var hyperlinkListener: HyperlinkListener? = null,
    var type: VisualLintErrorType? = null
  ) {

    fun summary(summary: String) = apply { this.summary = summary }

    fun severity(severity: HighlightSeverity) = apply { this.severity = severity }

    fun contentDescriptionProvider(provider: (Int) -> HtmlBuilder) = apply {
      this.contentDescriptionProvider = provider
    }

    fun model(model: NlModel) = apply { this.model = model }

    fun components(components: MutableList<NlComponent>) = apply { this.components = components }

    fun hyperlinkListener(hyperlinkListener: HyperlinkListener?) = apply {
      this.hyperlinkListener = hyperlinkListener
    }

    fun type(type: VisualLintErrorType) = apply { this.type = type }

    fun build(): VisualLintRenderIssue {
      requireNotNull(summary)
      requireNotNull(severity)
      requireNotNull(contentDescriptionProvider)
      requireNotNull(model)
      requireNotNull(components)
      requireNotNull(type)

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
   *
   * @param model Currently displaying model.
   */
  fun shouldHighlight(model: NlModel): Boolean
}
