/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.ATTR_IGNORE_A11Y_LINTS
import com.android.SdkConstants.TOOLS_URI
import com.android.resources.ResourceType
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.ui.resourcechooser.util.createResourcePickerDialog
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.android.tools.idea.uibuilder.property.support.PICK_A_RESOURCE
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.describe
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.summarize
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.lint.detector.api.Category
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableCollection
import com.intellij.lang.annotation.HighlightSeverity
import java.util.EnumSet
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener

/** Lint integrator for issues created by ATF (Accessibility Testing Framework) */
class AccessibilityLintIntegrator(issueModel: IssueModel) {

  @VisibleForTesting
  val issueProvider: IssueProvider =
    object : IssueProvider() {
      override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
        issues.forEach { issueListBuilder.add(it) }
      }
    }

  /** Returns the list of accessibility issues created by ATF. */
  val issues = HashSet<Issue>()

  init {
    issueModel.addIssueProvider(issueProvider)
  }

  /** Clear all lints and disable atf lint. */
  fun clear() {
    issues.clear()
  }

  /** Populate lints based on issues created */
  fun populateLints() {
    issueProvider.notifyModified()
  }

  /**
   * Creates a single issue/lint that matches given parameters. Must call [populateLints] in order
   * for issues to be visible.
   */
  fun createIssue(
    result: ValidatorData.Issue,
    component: NlComponent,
    eventListener: NlAtfIssue.EventListener? = null,
  ) {
    component.getAttribute(TOOLS_URI, ATTR_IGNORE)?.let {
      if (it.contains(result.mSourceClass) || it.contains(ATTR_IGNORE_A11Y_LINTS)) {
        return
      }
    }

    issues.add(NlAtfIssue(result, NlComponentIssueSource(component), eventListener))
  }
}

/** Issue created by [ValidatorData.Issue] */
class NlAtfIssue(
  val result: ValidatorData.Issue,
  issueSource: NlComponentIssueSource,
  private val eventListener: EventListener? = null,
) : Issue() {

  /** Event listeners for the ATF issue */
  interface EventListener {

    /** Called when the fix button is clicked by user */
    fun onApplyFixButtonClicked(issue: ValidatorData.Issue)

    /** Called when the ignore button is clicked by user */
    fun onIgnoreButtonClicked(issue: ValidatorData.Issue)
  }

  override val summary: String
    get() = result.summarize()

  override val description: String
    get() = result.describe()

  override val severity: HighlightSeverity
    get() {
      return when (result.mLevel) {
        ValidatorData.Level.ERROR -> HighlightSeverity.ERROR
        ValidatorData.Level.WARNING -> HighlightSeverity.WARNING
        else -> HighlightSeverity.INFORMATION
      }
    }

  override val source: IssueSource = issueSource

  override val category: String = Category.A11Y.name

  override val fixes: Stream<Fix>
    get() {
      val source = this.source
      if (source is NlAttributesHolder) {
        result.mFix?.let {
          val fix =
            Fix("Fix", it.description) {
              applyFixWrapper(it)
              eventListener?.onApplyFixButtonClicked(result)
            }
          return Stream.of(fix)
        }
      }
      return Stream.empty()
    }

  override val suppresses: Stream<Suppress>
    get() {
      val source = this.source
      if (source !is NlAttributesHolder) {
        return Stream.empty()
      }
      val ignore =
        Suppress("Suppress", "Suppress this check if it is false positive.") {
          val attr =
            source.getAttribute(TOOLS_URI, ATTR_IGNORE).let {
              if (it.isNullOrEmpty()) result.mSourceClass else "$it,${result.mSourceClass}"
            }
          // Set attr automatically refreshes the surface.
          source.setAttribute(TOOLS_URI, ATTR_IGNORE, attr)
          eventListener?.onIgnoreButtonClicked(result)
        }
      return Stream.of(ignore)
    }

  override val hyperlinkListener: HyperlinkListener?
    get() {
      if (result.mHelpfulUrl.isNullOrEmpty()) {
        return null
      }
      return createDefaultHyperLinkListener()
    }

  /** Returns the source class from [ValidatorData.Issue]. Used for metrics */
  val srcClass: String = result.mSourceClass

  /** For compound fixes, all fixes should be gathered into one single undoable action. */
  private fun applyFixWrapper(fix: ValidatorData.Fix) {
    val source = source as NlComponentIssueSource
    if (fix is ValidatorData.SetViewAttributeFix && fix.mSuggestedValue.isEmpty()) {
      // If the suggested value is an empty string, let the user pick a string
      // resource as the suggested value
      source.component?.model?.let {
        applySetViewAttributeFixWithEmptySuggestedValue(it, fix.mViewAttribute)
      }
    } else {
      applyFixImpl(fix, source)
    }
  }

  /** Let the user to pick a new string resource as the suggested value. */
  private fun applySetViewAttributeFixWithEmptySuggestedValue(
    model: NlModel,
    viewAttribute: ValidatorData.ViewAttribute,
  ) {
    val source = source
    val dialog: ResourcePickerDialog =
      createResourcePickerDialog(
        dialogTitle = PICK_A_RESOURCE,
        currentValue = null,
        facet = model.facet,
        resourceTypes = EnumSet.of(ResourceType.STRING),
        defaultResourceType = null,
        showColorStateLists = false,
        showSampleData = false,
        showThemeAttributes = false,
        file = null,
      )
    if (dialog.showAndGet() && (source is NlComponentIssueSource)) {
      val resourceName = dialog.resourceName ?: return
      source.setAttribute(viewAttribute.mNamespaceUri, viewAttribute.mAttributeName, resourceName)
    }
  }
}

@VisibleForTesting
fun applyFixImpl(fix: ValidatorData.Fix, source: NlAttributesHolder) {
  when (fix) {
    is ValidatorData.RemoveViewAttributeFix ->
      source.removeAttribute(fix.mViewAttribute.mNamespaceUri, fix.mViewAttribute.mAttributeName)
    is ValidatorData.SetViewAttributeFix ->
      source.setAttribute(
        fix.mViewAttribute.mNamespaceUri,
        fix.mViewAttribute.mAttributeName,
        fix.mSuggestedValue,
      )
    is ValidatorData.CompoundFix -> fix.mFixes.forEach { applyFixImpl(it, source) }
    else -> {
      // Do not apply the fix
    }
  }
}
