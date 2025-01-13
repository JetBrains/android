/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.view.accessibility.AccessibilityNodeInfo
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlComponentBackendEmpty
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.parsers.PsiXmlTag
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.android.tools.idea.uibuilder.lint.getTextRange
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintOrigin
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer.VisualLintIssueContent
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.describe
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.isLowContrast
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.rendering.parsers.TagSnapshot
import com.android.utils.HtmlBuilder
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.designer.model.EmptyXmlTag
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.profile.codeInspection.InspectionProfileManager
import java.util.Objects
import java.util.stream.Stream
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private const val COLOR_BLIND_ISSUE_SUMMARY = "Insufficient color contrast for color blind users"
private const val VISUAL_LINT_ISSUE_CATEGORY = "Visual Lint Issue"

/** Lint issues that is generated from visual sources (e.g. Layout Validation) */
class VisualLintRenderIssue private constructor(builder: Builder) : Issue() {
  private val _models = builder.model?.let { mutableSetOf(it) } ?: mutableSetOf()
  private var isComponentSuppressed: (NlComponent) -> Boolean = { false }
  private val _components = builder.components!!
  private val allComponents
    get() = synchronized(_components) { _components.toList() }

  /** List of [NlComponent]s that have not been suppressed */
  val components
    get() = synchronized(_components) { _components.filterNot(isComponentSuppressed).toList() }

  val models
    get() = synchronized(_models) { _models.toSet() }

  val type: VisualLintErrorType = builder.type!!
  override val source = VisualLintIssueProvider.VisualLintIssueSource(models, components)
  override val summary = builder.summary!!
  override val severity = builder.severity!!
  override val category = VISUAL_LINT_ISSUE_CATEGORY
  override val hyperlinkListener = builder.hyperlinkListener

  /**
   * Returns true if the issue should be highlighting when selected.
   *
   * @param model Currently displaying model.
   */
  fun shouldHighlight(model: NlModel): Boolean {
    return components.map { it.model }.contains(model)
  }

  private val contentDescriptionProvider = builder.contentDescriptionProvider!!
  override val description: String
    get() = contentDescriptionProvider.invoke(unsuppressedModelCount).stringBuilder.toString()

  /** Returns the text range of the issue. */
  private var range: TextRange? = null

  private val suppressList: MutableList<Suppress> = mutableListOf()

  override val suppresses: Stream<Suppress>
    get() =
      suppressList.filter { it.action !is VisualLintSuppressTask || it.action.isValid() }.stream()

  private val fixList: MutableList<Fix> = mutableListOf()

  override val fixes: Stream<Fix>
    get() = fixList.stream()

  private var frozenNavigatable: Navigatable? = null

  val navigatable: Navigatable?
    get() =
      frozenNavigatable ?: components.firstOrNull { it.tag == EmptyXmlTag.INSTANCE }?.navigatable

  private var frozenAffectedFiles: List<VirtualFile> = emptyList()

  val affectedFiles: List<VirtualFile>
    get() =
      frozenAffectedFiles.ifEmpty {
        models
          .filter { model -> this.shouldHighlight(model) }
          .map {
            @kotlin.Suppress("UnstableApiUsage")
            BackedVirtualFile.getOriginFileIfBacked(it.virtualFile)
          }
          .distinct()
      }

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

  private var frozenUnsuppressedModelCount = -1

  /** Get the number of [NlModel] which is not suppressed. */
  private val unsuppressedModelCount: Int
    get() =
      if (frozenUnsuppressedModelCount >= 0) {
        frozenUnsuppressedModelCount
      } else {
        components.map { it.model }.distinct().count()
      }

  fun isSuppressed(): Boolean {
    return unsuppressedModelCount == 0
  }

  fun addSuppress(suppress: Suppress) {
    suppressList.add(suppress)
  }

  fun addFix(fix: Fix) {
    fixList.add(fix)
  }

  fun customizeIsSuppressed(isComponentSuppressedMethod: (NlComponent) -> Boolean) {
    isComponentSuppressed = isComponentSuppressedMethod
  }

  fun combineWithIssue(issue: VisualLintRenderIssue) {
    synchronized(_components) { _components.addAll(issue.allComponents) }
    synchronized(_models) { _models.addAll(issue.models) }
    issue.allComponents.forEach { source.addComponent(it) }
    issue.models.forEach { source.addModel(it) }
  }

  /**
   * This should be called when the previews that are at the origin of this issue have been
   * discarded, so that the issue won't be updated anymore.
   */
  fun freeze() {
    frozenNavigatable = navigatable
    frozenUnsuppressedModelCount = unsuppressedModelCount
    frozenAffectedFiles =
      models
        .filter { model -> this.shouldHighlight(model) }
        .map {
          @kotlin.Suppress("UnstableApiUsage")
          BackedVirtualFile.getOriginFileIfBacked(it.virtualFile)
        }
        .distinct()
    synchronized(_components) { _components.clear() }
    synchronized(_models) { _models.clear() }
  }

  /** This should be called when the issue is going to be reused for a new preview. */
  fun unfreeze() {
    frozenNavigatable = null
    frozenUnsuppressedModelCount = -1
    frozenAffectedFiles = emptyList()
  }

  /** Builder for [VisualLintRenderIssue] */
  data class Builder(
    var summary: String? = null,
    var severity: HighlightSeverity? = null,
    var contentDescriptionProvider: ((Int) -> HtmlBuilder)? = null,
    var model: NlModel? = null,
    var components: MutableList<NlComponent>? = null,
    var hyperlinkListener: HyperlinkListener? = null,
    var type: VisualLintErrorType? = null,
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

    private fun componentFromViewInfo(viewInfo: ViewInfo?, model: NlModel): NlComponent? {
      val accessibilityNodeInfo = viewInfo?.accessibilityObject
      if (accessibilityNodeInfo is AccessibilityNodeInfo) {
        return model.treeReader.findViewByAccessibilityId(accessibilityNodeInfo.sourceNodeId)
      }
      val tag =
        (viewInfo?.cookie as? TagSnapshot)?.tag as? PsiXmlTag
          ?: return model.treeReader.components.firstOrNull()
      return model.treeReader.findViewByTag(tag.psiXmlTag)
    }

    private fun getHyperlinkListener(
      issueOrigin: VisualLintOrigin,
      type: VisualLintErrorType,
    ): HyperlinkListener {
      val listener = createDefaultHyperLinkListener()
      return HyperlinkListener {
        listener.hyperlinkUpdate(it)
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          VisualLintUsageTracker.getInstance().trackClickHyperLink(type, issueOrigin)
        }
      }
    }

    private fun getSeverity(type: VisualLintErrorType, project: Project): HighlightSeverity {
      val key = HighlightDisplayKey.find(type.shortName)
      return key?.let {
        InspectionProfileManager.getInstance(project)
          .currentProfile
          .getErrorLevel(it, null)
          .severity
      } ?: HighlightSeverity.WARNING
    }

    /** Create [VisualLintRenderIssue] for the given [VisualLintIssueContent]. */
    fun createVisualLintRenderIssue(
      content: VisualLintIssueContent,
      model: NlModel,
      type: VisualLintErrorType,
    ): VisualLintRenderIssue {
      var issueType = type
      var summary = content.message
      var descriptionProvider = content.descriptionProvider
      if (
        type == VisualLintErrorType.ATF &&
          appliedColorBlindFilter(model) != ColorBlindMode.NONE &&
          content.atfIssue?.isLowContrast() == true
      ) {
        issueType = VisualLintErrorType.ATF_COLORBLIND
        summary = COLOR_BLIND_ISSUE_SUMMARY
        descriptionProvider = { count ->
          colorBLindModeDescriptionProvider(content.atfIssue, model, count)
        }
      }
      val component = componentFromViewInfo(content.view, model)
      val issueOrigin =
        if (component?.backend is NlComponentBackendEmpty) VisualLintOrigin.UI_CHECK
        else VisualLintOrigin.XML_LINTING
      VisualLintUsageTracker.getInstance().trackIssueCreation(issueType, issueOrigin, model.facet)
      return builder()
        .summary(summary)
        .severity(getSeverity(type, model.project))
        .model(model)
        .components(if (component == null) mutableListOf() else mutableListOf(component))
        .contentDescriptionProvider(descriptionProvider)
        .hyperlinkListener(getHyperlinkListener(issueOrigin, issueType))
        .type(issueType)
        .build()
    }

    fun appliedColorBlindFilter(model: NlModel) =
      ColorBlindMode.entries.firstOrNull {
        model.displaySettings.modelDisplayName.value?.startsWith(it.displayName) == true
      } ?: ColorBlindMode.NONE

    private val colorBLindModeDescriptionProvider:
      (ValidatorData.Issue, NlModel, Int) -> HtmlBuilder =
      { issue, model, count ->
        val colorBlindFilter = appliedColorBlindFilter(model).displayName
        val description = issue.describe()
        val contentDescription =
          StringBuilder()
            .append("Color contrast check fails for $colorBlindFilter ")
            .append(
              when (count) {
                0,
                1 -> "colorblind configuration"
                2 -> "and 1 other colorblind configuration"
                else -> "and ${count - 1} other colorblind configurations"
              }
            )
            .append(".<br>")
            .append(description)
            .toString()
        HtmlBuilder().addHtml(contentDescription)
      }
  }
}
