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
package com.android.tools.idea.insights.inspection

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.GenerateErrorReason
import com.android.tools.idea.insights.PROJECT_ROOT_PREFIX
import com.android.tools.idea.insights.RepoInfo
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.ui.AppInsightsGutterRenderer
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AppInsightsExternalAnnotatorOnChangeTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)
  private val changeAwareFlagRule =
    FlagRule(StudioFlags.APP_INSIGHTS_CHANGE_AWARE_ANNOTATION_SUPPORT, true)

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(projectRule).around(vcsInsightsRule).around(changeAwareFlagRule)

  private lateinit var validAppVcsInfo: AppVcsInfo.ValidInfo
  private lateinit var errorAppVcsInfo: AppVcsInfo.Error

  @Before
  fun setUp() {
    validAppVcsInfo =
      AppVcsInfo.ValidInfo(
        listOf(
          RepoInfo(vcsKey = VCS_CATEGORY.TEST_VCS, rootPath = PROJECT_ROOT_PREFIX, revision = "1")
        )
      )

    errorAppVcsInfo = AppVcsInfo.Error(GenerateErrorReason.NO_VALID_GIT_FOUND)
  }

  private val document
    get() = projectRule.fixture.editor.document

  @Test
  fun `annotations are not shifted if error vcs info`() {
    val original = listOf(buildAppInsight(Frame(line = 4), buildIssue(errorAppVcsInfo)))

    withFakedInsights(original)

    checkAnnotationsOnChange(
      fileName = "MainActivity.kt",
      beforeSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      afterSource =
        """
          package test.simple

          class MainActivity {
              // TODO:
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      beforeLineToInsights = listOf(LineToInsights(3, original)),
      afterLineToInsights = listOf(LineToInsights(3, original)),
    )
  }

  @Test
  fun `annotations are not shifted if no vcs info`() {
    val original = listOf(buildAppInsight(Frame(line = 4), buildIssue(AppVcsInfo.NONE)))

    withFakedInsights(original)

    checkAnnotationsOnChange(
      fileName = "MainActivity.kt",
      beforeSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      afterSource =
        """
          package test.simple

          class MainActivity {
              // TODO:
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      beforeLineToInsights = listOf(LineToInsights(3, original)),
      afterLineToInsights = listOf(LineToInsights(3, original)),
    )
  }

  @Test
  fun `annotations are shifted when adding a line`() {
    val original = listOf(buildAppInsight(Frame(line = 4), buildIssue(validAppVcsInfo)))

    withFakedInsights(original)

    checkAnnotationsOnChange(
      fileName = "MainActivity.kt",
      beforeSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      afterSource =
        """
          package test.simple

          class MainActivity {
              // TODO:
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      beforeLineToInsights = listOf(LineToInsights(3, original)),
      afterLineToInsights = listOf(LineToInsights(4, listOf(original[0].updateLineNumber(4)))),
    )
  }

  @Test
  fun `annotations are shifted when deleting a line`() {
    val original = listOf(buildAppInsight(Frame(line = 5), buildIssue(validAppVcsInfo)))

    withFakedInsights(original)

    checkAnnotationsOnChange(
      fileName = "MainActivity.kt",
      beforeSource =
        """
          package test.simple

          class MainActivity {
              // TODO:
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      afterSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      beforeLineToInsights = listOf(LineToInsights(4, original)),
      afterLineToInsights = listOf(LineToInsights(3, listOf(original[0].updateLineNumber(3)))),
    )
  }

  @Test
  fun `annotations are gone when touching the line`() {
    val expected = listOf(buildAppInsight(Frame(line = 4), buildIssue(validAppVcsInfo)))

    withFakedInsights(expected)

    checkAnnotationsOnChange(
      fileName = "MainActivity.kt",
      beforeSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      afterSource =
        """
          package test.simple

          class MainActivity {
              fun onCreate() { //TODO:
              }
          }
      """
          .trimIndent(),
      beforeLineToInsights = listOf(LineToInsights(3, expected)),
      afterLineToInsights = emptyList(),
    )
  }

  private fun checkAnnotationsOnChange(
    fileName: String,
    beforeSource: String,
    afterSource: String,
    beforeLineToInsights: List<LineToInsights>,
    afterLineToInsights: List<LineToInsights>,
  ) {
    val psiFile = projectRule.fixture.addFileToProject("src/$fileName", beforeSource)
    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    val results =
      projectRule.fixture.doHighlighting().filter {
        it.gutterIconRenderer is AppInsightsGutterRenderer
      }
    document.assertHighlightResults(results, beforeLineToInsights)

    // Since no revision difference in our FakeContentRevision, we just
    // mimic some document changes by not saving any.
    document.updateFileContentWithoutSaving(afterSource, projectRule.project)

    val updated =
      projectRule.fixture.doHighlighting().filter {
        it.gutterIconRenderer is AppInsightsGutterRenderer
      }
    document.assertHighlightResults(updated, afterLineToInsights)
  }

  private fun AppInsight.updateLineNumber(line: Int): AppInsight {
    return copy(line = line)
  }
}
