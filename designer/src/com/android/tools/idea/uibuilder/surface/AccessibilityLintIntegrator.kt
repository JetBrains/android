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
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.lint.detector.api.Category
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableCollection
import com.intellij.lang.annotation.HighlightSeverity
import java.awt.Desktop
import java.net.URL
import java.util.stream.Stream
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Lint integrator for issues created by ATF (Accessibility Testing Framework)
 */
class AccessibilityLintIntegrator(issueModel: IssueModel) {

  @VisibleForTesting
  val issueProvider: IssueProvider = object : IssueProvider() {
    override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
      issues.forEach {
        issueListBuilder.add(it)
      }
    }
  }

  /**  Returns the list of accessibility issues created by ATF. */
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
   * Creates a single issue/lint that matches given parameters. Must call [populateLints]
   * in order for issues to be visible.
   */
  fun createIssue(result: ValidatorData.Issue, component: NlComponent?) {
    component?.getAttribute(TOOLS_URI, ATTR_IGNORE)?.let {
      if (it.contains(result.mSourceClass)) {
        return
      }
    }

    val source = if (component == null) {
      IssueSource.NONE
    }
    else {
      IssueSource.fromNlComponent(component)
    }
    issues.add(NlAtfIssue(result, source))
  }

}

/**  Issue created by [ValidatorData.Issue] */
class NlAtfIssue(
  private val result: ValidatorData.Issue,
  issueSource: IssueSource): Issue() {

  companion object {
    private const val CONTENT_LABELING = "CONTENT_LABELING"
    private const val TOUCH_TARGET_SIZE = "TOUCH_TARGET_SIZE"
    private const val LOW_CONTRAST = "LOW_CONTRAST"
  }

  override val summary: String
    get() =
      when (result.mCategory) {
        CONTENT_LABELING -> "Content labels missing or ambiguous"
        TOUCH_TARGET_SIZE -> "Touch size too small"
        LOW_CONTRAST -> "Low contrast"
        else -> "Accessibility Issue"
      }

  override val description: String
    get() {
      if (result.mHelpfulUrl.isNullOrEmpty()) {
        return result.mMsg
      }
      return """${result.mMsg}<br><br>Learn more at <a href="${result.mHelpfulUrl}">${result.mHelpfulUrl}</a>"""
    }

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
      if (source is NlAttributesHolder) {
        val fixes:MutableList<Fix> = mutableListOf();
        result.mFix?.let {
          val fix = Fix("Fix", it.description) {
            applyFixWrapper(it)
          }
          fixes.add(fix)
        }

        val ignore = Fix("Ignore", "Ignore this check if it is false positive.") {
          var attr = source.getAttribute(TOOLS_URI, ATTR_IGNORE)
          if (attr != null && attr.isNotEmpty()) {
            attr = "$attr,${result.mSourceClass}"
          } else {
            attr = result.mSourceClass
          }

          // Set attr automatically refreshes the surface.
          source.setAttribute(TOOLS_URI, ATTR_IGNORE, attr)
        }
        fixes.add(ignore)
        return fixes.stream()
      }
      return Stream.empty()
    }

  override val hyperlinkListener: HyperlinkListener?
    get() {
      if (result.mHelpfulUrl.isNullOrEmpty()) {
        return null
      }
      return HyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          Desktop.getDesktop().browse(URL(result.mHelpfulUrl).toURI())
        }
      }
    }

  /** Returns the source class from [ValidatorData.Issue]. Used for metrics */
  val srcClass: String = result.mSourceClass

  /**
   * For compound fixes, all fixes should be gathered into one single undoable action.
   */
  private fun applyFixWrapper(fix: ValidatorData.Fix) {
    if (source is NlComponentIssueSource) {
      NlWriteCommandActionUtil.run(source.component, "Update issue source") {
        applyFixImpl(fix, source.component)
      }
    }
  }

  private fun applyFixImpl(fix: ValidatorData.Fix, component: NlComponent) {
    when (fix) {
      is ValidatorData.RemoveViewAttributeFix ->
        component.removeAttribute(fix.mViewAttribute.mNamespaceUri,
                                  fix.mViewAttribute.mAttributeName)
      is ValidatorData.SetViewAttributeFix ->
        component.setAttribute(fix.mViewAttribute.mNamespaceUri,
                               fix.mViewAttribute.mAttributeName,
                               fix.mSuggestedValue)
      is ValidatorData.CompoundFix ->
        fix.mFixes.forEach {
          applyFixImpl(it, component)
        }
      else -> {
        // Do not apply the fix
      }
    }
  }
}
