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

import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.createNlModelForCompose
import com.android.tools.idea.compose.gradle.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.testing.virtualFile
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue.Companion.createVisualLintRenderIssue
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.visuallint.VisualLintErrorType
import com.android.tools.visuallint.analyzers.AtfAnalyzer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class AtfAnalyzerComposeTest {
  @get:Rule val projectRule = ComposeGradleProjectRule(projectPath = SIMPLE_COMPOSE_PROJECT_PATH)

  @Test
  fun testColorContrastIssueOnNotVisiblePreview() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.VisualLintPreviewKt.ColorContrastIssuePreview"
      )
    val uiCheckPreviews =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")

    val issues = collectIssuesFromRenders(uiCheckPreviews, facet, visualLintPreviewFile)
    val issueMessages = issues.map { it.summary }.distinct()

    Assert.assertEquals(2, issueMessages.size)
    Assert.assertEquals("Insufficient text color contrast ratio", issueMessages[0])
    Assert.assertEquals("Insufficient color contrast for color blind users", issueMessages[1])
  }

  @Test
  fun testOneColorblindProblemFound() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.VisualLintPreviewKt.OneColorBlindErrorPreview"
      )

    val uiCheckPreviews =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")
    val issues =
      collectIssuesFromRenders(uiCheckPreviews, facet, visualLintPreviewFile).filter {
        it.type == VisualLintErrorType.ATF_COLORBLIND
      }

    Assert.assertEquals(1, issues.size)

    val selectedIssueToShowInProblems = issues.first()

    Assert.assertEquals(
      "Insufficient color contrast for color blind users",
      selectedIssueToShowInProblems.summary,
    )

    val problemDescriptionHtml = selectedIssueToShowInProblems.description

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Tritanopes colorblind configuration" in problemDescriptionHtml
    )
  }

  @Test
  fun testTwoColorblindProblemsFound() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.VisualLintPreviewKt.TwoColorBlindErrorsPreview"
      )

    val uiCheckPreviews =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")
    val issues =
      collectIssuesFromRenders(uiCheckPreviews, facet, visualLintPreviewFile).filter {
        it.type == VisualLintErrorType.ATF_COLORBLIND
      }

    Assert.assertEquals(2, issues.size)

    // All the problems have the same message but different descriptions
    issues.forEach {
      Assert.assertEquals("Insufficient color contrast for color blind users", it.summary)
    }

    val selectedIssueToShowInProblems = issues.first().apply { combineWithIssue(issues[1]) }

    val problemDescriptionHtml = selectedIssueToShowInProblems.description

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Deuteranopes and 1 other colorblind configuration" in
        problemDescriptionHtml
    )
  }

  @Test
  fun testThreeColorblindProblemsFound() {
    val elementInstanceTest =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "google.simpleapplication.VisualLintPreviewKt.ThreeColorBlindErrorPreview"
      )

    val uiCheckPreviews =
      UiCheckModeFilter.Enabled.calculatePreviews(elementInstanceTest, isWearPreview = false)

    val facet = projectRule.androidFacet(":app")
    val visualLintPreviewFile =
      facet.virtualFile("src/main/java/google/simpleapplication/VisualLintPreview.kt")
    val issues =
      collectIssuesFromRenders(uiCheckPreviews, facet, visualLintPreviewFile).filter {
        it.type == VisualLintErrorType.ATF_COLORBLIND
      }

    Assert.assertEquals(3, issues.size)

    // All the problems have the same message but different descriptions
    issues.forEach {
      Assert.assertEquals("Insufficient color contrast for color blind users", it.summary)
    }

    val selectedIssueToShowInProblems =
      issues.first().apply {
        combineWithIssue(issues[1])
        combineWithIssue(issues[2])
      }

    val problemDescriptionHtml = selectedIssueToShowInProblems.description

    // Don't test the whole problemDescriptionHtml string because part of the content is provided
    // from ATF
    Assert.assertTrue(
      "Color contrast check fails for Deuteranopes and 2 other colorblind configurations" in
        problemDescriptionHtml
    )
  }

  private fun collectIssuesFromRenders(
    uiCheckPreviews: Collection<PsiComposePreviewElementInstance>,
    facet: AndroidFacet,
    targetFile: VirtualFile,
  ): List<VisualLintRenderIssue> =
    uiCheckPreviews.flatMap {
      val renderResult =
        renderPreviewElementForResult(
            facet = facet,
            originFile = targetFile,
            previewElement = it,
            useLayoutScanner = true,
            customViewInfoParser = accessibilityBasedHierarchyParser,
          )
          .get()

      val file = renderResult.lightVirtualFile
      val nlModel = createNlModelForCompose(projectRule.fixture.testRootDisposable, facet, file)
      nlModel.displaySettings.setDisplayName(it.displaySettings.name)

      // We need to update the hierarchy with the render result so that ATF can link the result with
      // the NlModel
      NlModelHierarchyUpdater.updateHierarchy(renderResult.result!!, nlModel)
      AtfAnalyzer.findIssues(renderResult.result, nlModel.configuration).map {
        createVisualLintRenderIssue(it, nlModel, VisualLintErrorType.ATF)
      }
    }
}
