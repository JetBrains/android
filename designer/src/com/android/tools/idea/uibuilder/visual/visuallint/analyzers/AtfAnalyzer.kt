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
package com.android.tools.idea.uibuilder.visual.visuallint.analyzers

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer.VisualLintIssueContent
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.android.tools.idea.validator.ValidatorUtil
import com.android.tools.rendering.RenderResult
import com.android.utils.HtmlBuilder
import java.util.EnumSet

/** [VisualLintAnalyzer] for issues coming from the Accessibility Testing Framework. */
object AtfAnalyzer : VisualLintAnalyzer() {
  override val type: VisualLintErrorType
    get() = VisualLintErrorType.ATF

  init {
    // Enable retrieving text character locations from TextView to improve the
    // accuracy of TextContrastCheck in ATF.
    LayoutValidator.setObtainCharacterLocations(true)
  }

  /** Analyze the given [RenderResult] for issues related to ATF that overlaps with visual lint. */
  override fun findIssues(
    renderResult: RenderResult,
    model: NlModel,
  ): List<VisualLintIssueContent> {
    when (val validatorResult = renderResult.validatorResult) {
      is ValidatorHierarchy -> {
        if (!validatorResult.isHierarchyBuilt) {
          // Result not available
          return ArrayList<VisualLintIssueContent>()
        }

        val policy =
          ValidatorData.Policy(
            EnumSet.of(ValidatorData.Type.ACCESSIBILITY, ValidatorData.Type.RENDER),
            EnumSet.of(
              ValidatorData.Level.ERROR,
              ValidatorData.Level.WARNING,
              ValidatorData.Level.INFO,
              ValidatorData.Level.VERBOSE,
            ),
          )

        val validated = ValidatorUtil.generateResults(policy, validatorResult)
        return validateAndUpdateLint(renderResult, validated)
      }
      else -> {
        // Result not available.
        return ArrayList<VisualLintIssueContent>()
      }
    }
  }
}

private fun validateAndUpdateLint(
  renderResult: RenderResult,
  validatorResult: ValidatorResult,
): MutableList<VisualLintIssueContent> {
  val issues = ArrayList<VisualLintIssueContent>()
  val accessibilityToViewInfo = mutableMapOf<AccessibilityNodeInfo, ViewInfo>()
  val viewToViewInfo = mutableMapOf<View, ViewInfo>()
  val viewsToAnalyze = ArrayDeque(renderResult.rootViews)
  while (viewsToAnalyze.isNotEmpty()) {
    val viewInfo = viewsToAnalyze.removeLast()
    viewInfo.children.forEach { viewsToAnalyze.addLast(it) }
    if (viewInfo.accessibilityObject is AccessibilityNodeInfo) {
      accessibilityToViewInfo[viewInfo.accessibilityObject as AccessibilityNodeInfo] = viewInfo
    } else if (viewInfo.viewObject is View) {
      viewToViewInfo[viewInfo.viewObject as View] = viewInfo
    }
  }
  validatorResult.issues.forEach {
    if (
      (it.mLevel == ValidatorData.Level.ERROR || it.mLevel == ValidatorData.Level.WARNING) &&
        it.mType == ValidatorData.Type.ACCESSIBILITY
    ) {
      val viewInfo =
        viewInfoFromSrcId(validatorResult, it.mSrcId, accessibilityToViewInfo, viewToViewInfo)
      issues.add(
        VisualLintIssueContent(view = viewInfo, message = it.summarize(), atfIssue = it) {
          count: Int ->
          HtmlBuilder().addHtml(it.describe())
        }
      )
    }
  }
  return issues
}

private fun viewInfoFromSrcId(
  validatorResult: ValidatorResult,
  id: Long,
  accessibilityToViewInfo: MutableMap<AccessibilityNodeInfo, ViewInfo>,
  viewToViewInfo: MutableMap<View, ViewInfo>,
): ViewInfo? {
  val view = validatorResult.srcMap[id]
  if (view != null) {
    val viewInfo = viewToViewInfo[view]
    if (viewInfo != null) {
      return viewInfo
    }
  }
  val node = validatorResult.nodeInfoMap[id] ?: return null
  return accessibilityToViewInfo[node]
}

private const val CONTENT_LABELING = "CONTENT_LABELING"
private const val TOUCH_TARGET_SIZE = "TOUCH_TARGET_SIZE"
private const val LOW_CONTRAST = "LOW_CONTRAST"

fun ValidatorData.Issue.isLowContrast(): Boolean {
  return mSourceClass == "TextContrastCheck" ||
    mSourceClass == "ImageContrastCheck" ||
    mCategory == LOW_CONTRAST
}

fun ValidatorData.Issue.summarize() =
  when (mSourceClass) {
    "SpeakableTextPresentCheck" -> "No speakable text present"
    "EditableContentDescCheck" -> "Editable text view with contentDescription"
    "TouchTargetSizeCheck" -> "Touch target size too small"
    "DuplicateSpeakableTextCheck" -> "Duplicate speakable text present"
    "TextContrastCheck" -> "Insufficient text color contrast ratio"
    "ClickableSpanCheck" -> "Accessibility issue with clickable span"
    "DuplicateClickableBoundsCheck" -> "Duplicated clickable Views"
    "RedundantDescriptionCheck" -> "Item labelled with type or state"
    "ImageContrastCheck" -> "Insufficient image color contrast ratio"
    "ClassNameCheck" -> "Accessibility Issue"
    "TraversalOrderCheck" -> "Unpredictable traversal behavior"
    "LinkPurposeUnclearCheck" -> "Unclear text in link"
    else -> {
      when (mCategory) {
        CONTENT_LABELING -> "Content labels missing or ambiguous"
        TOUCH_TARGET_SIZE -> "Touch target size too small"
        LOW_CONTRAST -> "Insufficient color contrast ratio"
        else -> "Accessibility Issue"
      }
    }
  }

fun ValidatorData.Issue.describe(): String =
  if (mHelpfulUrl.isNullOrEmpty()) {
    mMsg
  } else {
    """$mMsg<br><br>Learn more at <a href="$mHelpfulUrl">$mHelpfulUrl</a>"""
  }
