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

import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.NlComponentIssueSource
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.surface.NlAtfIssue
import com.android.tools.idea.uibuilder.surface.NlScannerLayoutParser
import com.android.tools.idea.uibuilder.surface.RenderResultMetricData
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.android.tools.idea.validator.ValidatorUtil
import com.android.tools.rendering.RenderResult
import java.util.EnumSet

class VisualLintAtfAnalysis(private val model: NlModel) {

  /**
   * Parses the layout and stores all metadata required for linking issues to source [NlComponent]
   */
  private val layoutParser = NlScannerLayoutParser()

  /** Render specific metrics data */
  var renderMetric = RenderResultMetricData()

  init {
    // Enable retrieving text character locations from TextView to improve the
    // accuracy of TextContrastCheck in ATF.
    LayoutValidator.setObtainCharacterLocations(true)
  }

  fun pause() {
    LayoutValidator.setPaused(true)
  }

  fun resume() {
    LayoutValidator.setPaused(false)
  }

  /** Validate the layout and update the lint accordingly. */
  fun validateAndUpdateLint(renderResult: RenderResult): List<VisualLintAtfIssue> {
    when (val validatorResult = renderResult.validatorResult) {
      is ValidatorHierarchy -> {
        if (!validatorResult.isHierarchyBuilt) {
          // Result not available
          return ArrayList<VisualLintAtfIssue>()
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
        return ArrayList<VisualLintAtfIssue>()
      }
    }
  }

  private fun validateAndUpdateLint(
    renderResult: RenderResult,
    validatorResult: ValidatorResult,
  ): MutableList<VisualLintAtfIssue> {
    layoutParser.clear()

    val issues = ArrayList<VisualLintAtfIssue>()

    try {
      val components = model.treeReader.components
      if (components.isEmpty()) {
        // Result not available.
        return issues
      }

      var issuesWithoutSources = 0
      val root = components[0]
      layoutParser.buildViewToComponentMap(root)
      validatorResult.issues.forEach {
        if (
          (it.mLevel == ValidatorData.Level.ERROR || it.mLevel == ValidatorData.Level.WARNING) &&
            it.mType == ValidatorData.Type.ACCESSIBILITY
        ) {
          val component =
            layoutParser.findComponent(it, validatorResult.srcMap, validatorResult.nodeInfoMap)
          if (component == null) {
            issuesWithoutSources++
          } else {
            issues.add(VisualLintAtfIssue(it, component))
          }
        }
      }
    } finally {
      renderMetric.renderMs = renderResult.stats.renderDurationMs
      renderMetric.scanMs = validatorResult.metric.mHierarchyCreationMs
      renderMetric.componentCount = layoutParser.componentCount
      renderMetric.isRenderResultSuccess = renderResult.renderResult.isSuccess

      layoutParser.clear()
    }
    return issues
  }
}

class VisualLintAtfIssue(result: ValidatorData.Issue, component: NlComponent) :
  NlAtfIssue(result, NlComponentIssueSource(component)), VisualLintHighlightingIssue {

  private val visualLintIssueSource =
    VisualLintIssueProvider.VisualLintIssueSource(setOf(component.model), listOf(component))
  private val sourceModel: NlModel?
    get() = visualLintIssueSource.components.firstOrNull()?.model

  override val source: IssueSource
    get() = visualLintIssueSource

  override fun shouldHighlight(model: NlModel): Boolean {
    return sourceModel == model
  }

  fun appliedColorBlindFilter(): ColorBlindMode {
    return ColorBlindMode.values().firstOrNull {
      sourceModel?.displaySettings?.modelDisplayName?.value?.startsWith(it.displayName) ?: false
    } ?: ColorBlindMode.NONE
  }
}
