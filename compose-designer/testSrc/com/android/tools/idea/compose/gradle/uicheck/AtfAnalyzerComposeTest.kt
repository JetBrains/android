/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.uicheck

import com.android.tools.idea.compose.UiCheckModeFilter
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.createNlModelForCompose
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintAnalyzer.VisualLintIssueContent
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzer
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.SingleComposePreviewElementInstance
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class AtfAnalyzerComposeTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(projectPath = SIMPLE_COMPOSE_PROJECT_PATH)

  @After
  fun tearDown() {
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.clearOverride()
  }

  @Test
  fun testColorContrastIssueOnAllPreviews() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.VisualLintPreviewKt.ColorContrastIssuePreview",
      )
    val uiCheckPreviews = UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest)

    val facet = projectRule.androidFacet(":app")

    val issues = collectIssuesFromRenders(uiCheckPreviews, facet)
    val issueMessages = issues.map { it.message }.distinct()

    Assert.assertEquals(1, issueMessages.size)
    Assert.assertEquals("Insufficient text color contrast ratio", issueMessages[0])
  }

  @Test
  fun testNoColorBlindIssuesWhenFeatureFlagOff() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.VisualLintPreviewKt.ThreeColorBlindErrorPreview",
      )

    val uiCheckPreviews = UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest)

    val facet = projectRule.androidFacet(":app")
    val issues = collectIssuesFromRenders(uiCheckPreviews, facet)

    Assert.assertTrue(issues.isEmpty())
  }

  @Test
  fun testOneColorblindProblemFound() {
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)

    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.VisualLintPreviewKt.OneColorBlindErrorPreview",
      )

    val uiCheckPreviews = UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest)

    val facet = projectRule.androidFacet(":app")
    val issues = collectIssuesFromRenders(uiCheckPreviews, facet)

    Assert.assertEquals(1, issues.size)

    val selectedIssueToShowInProblems = issues.first()

    Assert.assertEquals(
      "Insufficient color contrast for color blind users",
      selectedIssueToShowInProblems.message,
    )

    val problemDescriptionHtml =
      selectedIssueToShowInProblems.descriptionProvider(issues.size).stringBuilder.toString()

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Tritanopes colorblind configuration" in
        problemDescriptionHtml,
    )
  }

  @Test
  fun testTwoColorblindProblemsFound() {
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)

    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.VisualLintPreviewKt.TwoColorBlindErrorsPreview",
      )

    val uiCheckPreviews = UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest)

    val facet = projectRule.androidFacet(":app")
    val issues = collectIssuesFromRenders(uiCheckPreviews, facet)

    Assert.assertEquals(2, issues.size)

    // All the problems have the same message but different descriptions
    issues.forEach {
      Assert.assertEquals(
        "Insufficient color contrast for color blind users",
        it.message,
      )
    }

    val selectedIssueToShowInProblems = issues.first()

    val problemDescriptionHtml =
      selectedIssueToShowInProblems.descriptionProvider(issues.size).stringBuilder.toString()

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Deuteranopes and 1 other colorblind configuration" in
        problemDescriptionHtml,
    )
  }

  @Test
  fun testThreeColorblindProblemsFound() {
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)

    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting(
        "google.simpleapplication.VisualLintPreviewKt.ThreeColorBlindErrorPreview",
      )

    val uiCheckPreviews = UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest)

    val facet = projectRule.androidFacet(":app")
    val issues = collectIssuesFromRenders(uiCheckPreviews, facet)

    Assert.assertEquals(3, issues.size)

    // All the problems have the same message but different descriptions
    issues.forEach {
      Assert.assertEquals(
        "Insufficient color contrast for color blind users",
        it.message,
      )
    }

    val selectedIssueToShowInProblems = issues.first()

    val problemDescriptionHtml =
      selectedIssueToShowInProblems.descriptionProvider(issues.size).stringBuilder.toString()

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Deuteranopes and 2 other colorblind configurations" in
        problemDescriptionHtml,
    )
  }

  private fun collectIssuesFromRenders(
    uiCheckPreviews: Collection<ComposePreviewElementInstance>,
    facet: AndroidFacet
  ): List<VisualLintIssueContent> =
    uiCheckPreviews.flatMap {
      val renderResult =
        renderPreviewElementForResult(
            facet = facet,
            previewElement = it,
            useLayoutScanner = true,
            customViewInfoParser = accessibilityBasedHierarchyParser,
          )
          .get()!!

      val file = renderResult.sourceFile.virtualFile
      val nlModel =
        createNlModelForCompose(
          projectRule.fixture.testRootDisposable,
          facet,
          file,
        )
      nlModel.modelDisplayName = it.displaySettings.name

      // We need to update the hierarchy with the render result so that ATF can link the result with
      // the NlModel
      NlModelHierarchyUpdater.updateHierarchy(renderResult, nlModel)
      AtfAnalyzer.findIssues(renderResult, nlModel)
    }
}
